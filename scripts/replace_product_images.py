"""
Fetch real product images from Lorem Flickr, upload to Aliyun OSS,
then update the `mall` database to bind the new OSS URLs.

Run: python e:/zhiwu-mall/scripts/replace_product_images.py
"""
import os
import time
import urllib.request
from pathlib import Path

import oss2
import pymysql

# ============== Config (read from environment variables) ==============
OSS_ENDPOINT = os.environ.get("ALIOSS_ENDPOINT", "oss-cn-beijing.aliyuncs.com")
OSS_ACCESS_KEY_ID = os.environ.get("ALIOSS_ACCESS_KEY_ID", "")
OSS_ACCESS_KEY_SECRET = os.environ.get("ALIOSS_ACCESS_KEY_SECRET", "")
OSS_BUCKET = os.environ.get("ALIOSS_BUCKET", "skyhyf")

DB_HOST = os.environ.get("MALL_DB_HOST", "localhost")
DB_PORT = int(os.environ.get("MALL_DB_PORT", "3306"))
DB_USER = os.environ.get("MALL_DB_USERNAME", "mall_app")
DB_PASSWORD = os.environ.get("MALL_DB_PASSWORD", "")
DB_NAME = os.environ.get("MALL_DB_NAME", "mall")

OSS_DIR = "product"  # OSS object key prefix
LOREMFLICKR_BASE = "https://loremflickr.com/800/800"
LOREMFLICKR_WIDE_BASE = "https://loremflickr.com/1280/720"
LOREMFLICKR_CARD_BASE = "https://loremflickr.com/800/600"
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}

# ============== Image definitions ==============
# Each image: key -> (keywords, base_url, size_label_for_log)
# Use lock param for idempotent reruns (same lock = same image)
IMAGES = {
    # --- Product main images (square 800x800) ---
    "dress_white": ("dress,fashion,white", LOREMFLICKR_BASE, 1),
    "dress_blue": ("dress,fashion,blue", LOREMFLICKR_BASE, 2),
    "tee_white": ("tshirt,white", LOREMFLICKR_BASE, 3),
    "jeans": ("jeans,denim", LOREMFLICKR_BASE, 4),
    "earphone_black": ("earphones,black", LOREMFLICKR_BASE, 5),
    "earphone_white": ("earphones,white", LOREMFLICKR_BASE, 6),
    "shorts": ("shorts,sports", LOREMFLICKR_BASE, 7),
    "suncoat": ("jacket,outdoor", LOREMFLICKR_BASE, 8),
    "watch": ("smartwatch", LOREMFLICKR_BASE, 9),
    "box": ("storage,box", LOREMFLICKR_BASE, 10),
    "earphone_blue": ("earphones,blue", LOREMFLICKR_BASE, 11),
    "earphone_pink": ("earphones,pink", LOREMFLICKR_BASE, 12),
    "tee_basic": ("tshirt,plain", LOREMFLICKR_BASE, 13),
    # --- Brand logos (square) ---
    "brand_zhiwu": ("logo,leaf,green", LOREMFLICKR_BASE, 14),
    "brand_tujiang": ("logo,rabbit,orange", LOREMFLICKR_BASE, 15),
    "brand_senye": ("logo,forest,tree", LOREMFLICKR_BASE, 16),
    "brand_polar": ("logo,ice,blue", LOREMFLICKR_BASE, 17),
    # --- Banners (wide 1280x720) ---
    "banner1": ("fashion,sale,shopping", LOREMFLICKR_WIDE_BASE, 18),
    "banner2": ("electronics,sale,technology", LOREMFLICKR_WIDE_BASE, 19),
    "banner3": ("vip,gold,luxury", LOREMFLICKR_WIDE_BASE, 20),
    "banner4": ("gadgets,technology,electronics", LOREMFLICKR_WIDE_BASE, 21),
    "banner5": ("home,decor,furniture", LOREMFLICKR_WIDE_BASE, 22),
    # --- Hot activity banners (wide) ---
    "hot_banner1": ("sale,discount,shopping", LOREMFLICKR_WIDE_BASE, 23),
    "hot_banner2": ("trending,hot,popular", LOREMFLICKR_WIDE_BASE, 24),
    "hot_banner3": ("shopping,cart,bags", LOREMFLICKR_WIDE_BASE, 25),
    "hot_banner4": ("new,arrival,fresh", LOREMFLICKR_WIDE_BASE, 26),
    # --- Home hot cards (4:3 800x600) ---
    "hot1": ("gift,box,present", LOREMFLICKR_CARD_BASE, 27),
    "hot2": ("popular,products,trending", LOREMFLICKR_CARD_BASE, 28),
    "hot3": ("shopping,variety", LOREMFLICKR_CARD_BASE, 29),
    "hot4": ("new,fresh,green", LOREMFLICKR_CARD_BASE, 30),
}

# ============== DB update mappings ==============
DB_UPDATES = {
    "product_image": {
        1: "dress_white",
        2: "dress_blue",
        3: "dress_white",
        4: "tee_white",
        5: "tee_white",
        6: "earphone_black",
        7: "earphone_black",
    },
    "product_sku": {
        1: "dress_white",
        2: "dress_white",
        3: "dress_blue",
        4: "dress_blue",
        5: "dress_white",
        6: "dress_blue",
        7: "tee_white",
        8: "tee_white",
        9: "tee_white",
        10: "earphone_black",
        11: "earphone_white",
        12: "earphone_blue",
        13: "earphone_pink",
    },
    "spec_value": {
        1: "dress_white",
        2: "dress_blue",
        9: "earphone_black",
        10: "earphone_white",
    },
    "brand": {
        1: "brand_zhiwu",
        2: "brand_tujiang",
        3: "brand_senye",
        4: "brand_polar",
    },
    "banner": {
        1: "banner1",
        2: "banner2",
        3: "banner3",
        4: "banner4",
        5: "banner5",
    },
    "hot_activity": {
        1: "hot_banner1",
        2: "hot_banner2",
        3: "hot_banner3",
        4: "hot_banner4",
    },
    "home_hot": {
        1: "hot1",
        2: "hot2",
        3: "hot3",
        4: "hot4",
    },
}

TABLE_COLUMN = {
    "product_image": "image_url",
    "product_sku": "picture",
    "spec_value": "picture",
    "brand": "logo",
    "banner": "img_url",
    "hot_activity": "banner_picture",
}


def fetch_image(keywords: str, base_url: str, lock: int) -> bytes:
    """Fetch image bytes from Lorem Flickr."""
    url = f"{base_url}/{keywords}?lock={lock}"
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        if r.status != 200:
            raise RuntimeError(f"Flickr returned status {r.status}")
        return r.read()


def upload_to_oss(bucket: oss2.Bucket, object_key: str, data: bytes) -> str:
    """Upload bytes to OSS, return public URL."""
    result = bucket.put_object(object_key, data)
    if result.status != 200:
        raise RuntimeError(f"OSS upload failed: status {result.status}")
    return f"https://{OSS_BUCKET}.{OSS_ENDPOINT}/{object_key}"


def main():
    auth = oss2.Auth(OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET)
    bucket = oss2.Bucket(auth, OSS_ENDPOINT, OSS_BUCKET)

    url_map = {}
    total = len(IMAGES)
    date_dir = time.strftime("%Y%m%d")

    print(f"=== Fetching {total} images from Lorem Flickr ===")
    for key, (keywords, base_url, lock) in IMAGES.items():
        object_key = f"{OSS_DIR}/{date_dir}/{key}.jpg"
        # Idempotent: skip if already uploaded
        if bucket.object_exists(object_key):
            url = f"https://{OSS_BUCKET}.{OSS_ENDPOINT}/{object_key}"
            url_map[key] = url
            print(f"[{lock}/{total}] SKIP (exists) {key}")
            continue

        print(f"[{lock}/{total}] Fetching {key} ({keywords}) ...", end=" ", flush=True)
        retry = 0
        while True:
            try:
                data = fetch_image(keywords, base_url, lock)
                url = upload_to_oss(bucket, object_key, data)
                url_map[key] = url
                print(f"OK ({len(data)} bytes) -> {url}")
                break
            except Exception as e:
                retry += 1
                if retry >= 3:
                    print(f"FAILED after 3 retries: {e}")
                    raise
                print(f"retry {retry} ({e})...", end=" ", flush=True)
                time.sleep(2)

    print(f"\n=== All {total} images uploaded ===\n")

    # Update database
    conn = pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASSWORD,
        database=DB_NAME, charset="utf8mb4", autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            total_updates = 0
            for table, mapping in DB_UPDATES.items():
                if table == "home_hot":
                    for row_id, img_key in mapping.items():
                        url = url_map[img_key]
                        sql = f"UPDATE `{table}` SET pictures = JSON_ARRAY(%s) WHERE id = %s"
                        cur.execute(sql, (url, row_id))
                        total_updates += 1
                        print(f"  {table} id={row_id} -> {url}")
                else:
                    col = TABLE_COLUMN[table]
                    for row_id, img_key in mapping.items():
                        url = url_map[img_key]
                        sql = f"UPDATE `{table}` SET `{col}` = %s WHERE id = %s"
                        cur.execute(sql, (url, row_id))
                        total_updates += 1
                        print(f"  {table} id={row_id} -> {url}")
            conn.commit()
            print(f"\n=== Updated {total_updates} rows ===")
    finally:
        conn.close()

    print("\nDone.")


if __name__ == "__main__":
    main()
