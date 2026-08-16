import pymysql

conn = pymysql.connect(
    host="localhost", port=3306, user="root", password="123456",
    database="mall", charset="utf8mb4"
)

try:
    with conn.cursor() as cur:
        print("=== Product count by category ===")
        cur.execute("""
            SELECT c.name, COUNT(p.id) as cnt 
            FROM product p 
            JOIN category c ON p.category_id = c.id 
            GROUP BY c.id, c.name
            ORDER BY cnt DESC
        """)
        for row in cur.fetchall():
            print(f"  {row[0]}: {row[1]}")

        print("\n=== Total stats ===")
        cur.execute("SELECT COUNT(*) FROM product")
        print(f"  Total products: {cur.fetchone()[0]}")
        cur.execute("SELECT COUNT(*) FROM product_sku")
        print(f"  Total SKUs: {cur.fetchone()[0]}")
        cur.execute("SELECT COUNT(*) FROM product_image")
        print(f"  Total images: {cur.fetchone()[0]}")
        cur.execute("SELECT COUNT(*) FROM spec")
        print(f"  Spec groups: {cur.fetchone()[0]}")

        print("\n=== Sample new products ===")
        cur.execute("""
            SELECT id, name, price, inventory, sales_count 
            FROM product 
            WHERE id > 10 
            ORDER BY id DESC 
            LIMIT 5
        """)
        for row in cur.fetchall():
            print(f"  ID={row[0]}: {row[1]} | ¥{row[2]} | stock={row[3]} | sales={row[4]}")
finally:
    conn.close()
