# =====================================================================
# Java 业务（mall-search-service 等）专用的 Elasticsearch 8.18.8 + IK 中文分词
# - 与 shopkeeper-agent 的 ES 8.19.10 并存，互不影响
# - 版本选 8.18.8：Spring Boot 3.5.14 的 Spring Data Elasticsearch 5.5
#   用的是 elasticsearch-java 8.18.8 客户端，需与 ES 服务端版本对齐
# - 基于 Elastic 官方镜像，安装匹配版本的 IK 8.18.8 + pinyin 8.18.8
# =====================================================================
FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.8

USER root

RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.18.8.zip

RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-pinyin/stable/elasticsearch-analysis-pinyin-8.18.8.zip

RUN chown -R elasticsearch:elasticsearch /usr/share/elasticsearch/plugins

# 预生成一个合法的空 keystore（无密码），避免运行时出现 0 字节 keystore 触发
# "read past EOF: NIOFSIndexInput(path=.../elasticsearch.keystore)" 崩溃
RUN /usr/share/elasticsearch/bin/elasticsearch-keystore create </dev/null \
    && chown elasticsearch:elasticsearch /usr/share/elasticsearch/config/elasticsearch.keystore

# 配置直接打进镜像（避免 Docker Desktop 单文件 bind-mount 的怪异行为）
COPY config/elasticsearch.yml /usr/share/elasticsearch/config/elasticsearch.yml
RUN chown elasticsearch:elasticsearch /usr/share/elasticsearch/config/elasticsearch.yml

USER elasticsearch
