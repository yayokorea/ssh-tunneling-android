# SSH Tunneling Android

Material 3 based Android app for one-tap SSH local port forwarding.

## MVP

- Compose settings screen for a single SSH tunnel profile
- Password or pasted PEM private key authentication
- Foreground service that keeps the SSH tunnel alive
- Home screen widget for one-tap connect and disconnect
- GitHub Actions workflow for remote build validation
# ADB 무선 디버깅 터널

ADB 연결/페어링 터널은 현재 Wi-Fi에서 `_adb-tls-connect._tcp.` 또는
`_adb-tls-pairing._tcp.` 서비스를 검색하고, 연결 가능한 자기 기기의 endpoint를
SSH 서버 `127.0.0.1:5555` 또는 `127.0.0.1:5556`으로 reverse forwarding합니다.
앱은 pairing code를 읽거나 저장하지 않습니다.

ADB 터널을 사용하기 전에 SSH 호스트의 SHA-256 host key fingerprint를 확인하고
호스트 프로필에 저장해야 합니다. ADB listener는 외부 인터페이스에 노출되지 않으며,
서버에서는 `GatewayPorts no`와 필요한 경우 `PermitListen 127.0.0.1:5555`/
`PermitListen 127.0.0.1:5556` 정책을 사용하세요.

설정 내보내기는 기본적으로 비밀값을 제외합니다. 기존 설정 파일은 schema version이
없는 v1로 읽고 일반 local forwarding으로 마이그레이션합니다.
