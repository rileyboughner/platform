# Build context is the repo root (see docker-compose.yml) so we can pull in
# the generated theme CSS directly -- no separate theme implementation here.
FROM python:3.12-slim
WORKDIR /app
# kubectl for the Kubernetes section (kubeconfig is mounted read-only).
ADD https://dl.k8s.io/release/v1.31.0/bin/linux/amd64/kubectl /usr/local/bin/kubectl
RUN chmod +x /usr/local/bin/kubectl
COPY theme/build/css/variables.css ./static/theme-variables.css
COPY dashboard/server.py .
COPY dashboard/static ./static
EXPOSE 8099
CMD ["python", "server.py"]
