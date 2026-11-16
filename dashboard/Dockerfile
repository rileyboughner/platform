# Build context is the repo root (see docker-compose.yml) so we can pull in
# the generated theme CSS directly -- no separate theme implementation here.
FROM python:3.12-slim
WORKDIR /app
COPY theme/build/css/variables.css ./static/theme-variables.css
COPY dashboard/server.py .
COPY dashboard/static ./static
EXPOSE 8099
CMD ["python", "server.py"]
