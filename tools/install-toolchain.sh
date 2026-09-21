#!/usr/bin/env bash
# Rebuilds the build toolchain this project needs. The sandbox keeps /opt outside the
# workspace, so it is wiped between sessions - run this once and the build works again.
#
#   bash tools/install-toolchain.sh
#
# Installs JDK 17 (Temurin), Gradle 8.2 and Android SDK platform 34 + build-tools 34.0.0,
# then prints the environment variables to use.
set -euo pipefail

DL=/opt/dl
sudo mkdir -p "$DL"
sudo chown "$USER":"$USER" "$DL"
cd "$DL"

echo "== downloading =="
[ -f jdk17.tar.gz ] || curl -sL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
[ -f gradle.zip ]   || curl -sL -o gradle.zip   "https://services.gradle.org/distributions/gradle-8.2-bin.zip"
[ -f cmdline.zip ]  || curl -sL -o cmdline.zip  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

echo "== unpacking =="
sudo mkdir -p /opt/jdk17 /opt/gradle-unzip /opt/android-sdk/cmdline-tools
sudo tar xzf jdk17.tar.gz -C /opt/jdk17 --strip-components=1
sudo unzip -q -o gradle.zip -d /opt/gradle-unzip
sudo unzip -q -o cmdline.zip -d /tmp/cmdline-tools-unpack
sudo rm -rf /opt/android-sdk/cmdline-tools/latest
sudo mv /tmp/cmdline-tools-unpack/cmdline-tools /opt/android-sdk/cmdline-tools/latest
sudo ln -sfn /opt/jdk17 /opt/jdk
sudo mkdir -p /opt/gradle
sudo ln -sfn /opt/gradle-unzip/gradle-8.2 /opt/gradle/gradle-8.2
sudo chown -R "$USER":"$USER" /opt/android-sdk
sudo mkdir -p /opt/gh && sudo chown "$USER":"$USER" /opt/gh

echo "== android sdk packages =="
mkdir -p /opt/android-sdk/licenses
printf "\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n8933bad161af4178b1185d1a37fbf41ea5269c55\n" \
  > /opt/android-sdk/licenses/android-sdk-license
cp /opt/android-sdk/licenses/android-sdk-license /opt/android-sdk/licenses/android-sdk-preview-license
export JAVA_HOME=/opt/jdk17
export PATH=/opt/jdk17/bin:$PATH
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"

cat <<'EOF'

== ready ==
export JAVA_HOME=/opt/jdk
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/opt/gh
export PATH=/opt/jdk/bin:$PATH
/opt/gradle/gradle-8.2/bin/gradle :core:test :app:assembleDebug :app:assembleRelease --console=plain

The first run downloads dependencies into /opt/gh (several minutes).
EOF
