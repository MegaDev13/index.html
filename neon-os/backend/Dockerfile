FROM python:3.12-slim
WORKDIR /app
COPY server.py /app/server.py
RUN useradd -u 10001 --create-home atlas && mkdir /data && chown atlas:atlas /data
USER atlas
ENV ATLAS_DB=/data/atlas.sqlite3 ATLAS_BIND=0.0.0.0
EXPOSE 8787
CMD ["python", "server.py"]
