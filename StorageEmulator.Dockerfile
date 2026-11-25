FROM node:22-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global firebase-tools@14

WORKDIR /workspace
CMD ["firebase", "emulators:start", "--only", "storage", "--project", "demo-attendiq", "--config", "firebase.storage-emulator.json"]
