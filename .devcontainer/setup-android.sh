#!/usr/bin/env bash
set -e
# VidMax Android SDK setup for GitHub Codespaces (8GB RAM / 32GB storage machine)
# Installs cmdline-tools + platform-tools + android-35 + build-tools 35.0.0

SDK_DIR="/home/vscode/Android/Sdk"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

mkdir -p "$SDK_DIR/cmdline-tools"
cd /tmp

if [ ! -d "$SDK_DIR/cmdline-tools/latest" ]; then
  echo "Downloading Android cmdline-tools..."
  curl -o cmdline-tools.zip "$CMDLINE_URL"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  unzip -q cmdline-tools.zip -d "$SDK_DIR/cmdline-tools/tmp"
  mv "$SDK_DIR/cmdline-tools/tmp/cmdline-tools/"* "$SDK_DIR/cmdline-tools/latest/"
  rm -rf "$SDK_DIR/cmdline-tools/tmp" cmdline-tools.zip
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

echo "Accepting licenses..."
yes | sdkmanager --licenses > /dev/null || true

echo "Installing platform-tools, android-35, build-tools 35.0.0..."
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "SDK packages installed:"
sdkmanager --list_installed || true

# Persist env for vscode shell
echo "export ANDROID_HOME=$SDK_DIR" >> ~/.bashrc
echo "export ANDROID_SDK_ROOT=$SDK_DIR" >> ~/.bashrc
echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH' >> ~/.bashrc

echo "Done. ANDROID_HOME=$SDK_DIR"
