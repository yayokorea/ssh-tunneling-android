# SSH Tunneling + ADB 자동 감지 통합 및 UI/UX 전면 개편 계획

## 1. 문서 목적

이 문서는 `adb-tunneling` Phase 0 프로젝트에서 검증한 Android 무선 디버깅 포트 자동 감지 기능을 `ssh_tunneling` 앱으로 통합하고, 앱의 화면 구조와 사용자 경험을 Jetpack Compose 및 Material Design 3 기준으로 전면 개편하기 위한 구현 계획이다.

이번 작업은 기존 화면에 ADB 입력란을 추가하는 수준으로 진행하지 않는다. 사용자가 SSH 포워딩의 세부 개념을 몰라도 다음 작업을 안전하게 수행할 수 있는 제품 경험을 목표로 한다.

- 일반 SSH 로컬 포워딩 생성 및 실행
- Android 무선 디버깅 connect 포트 자동 감지 및 SSH 역방향 포워딩
- Android pairing 포트 자동 감지 및 SSH 역방향 포워딩
- 현재 상태와 다음에 해야 할 행동을 즉시 이해
- 휴대전화, 태블릿, 폴더블에서 일관된 적응형 화면 사용
- 앱 화면을 닫거나 화면이 꺼져도 필요한 감지와 터널 유지

구현 대상 저장소는 `~/dev/ssh_tunneling`이다. `adb-tunneling`은 검증 코드의 원본 및 회귀 확인 자료로만 남기며 최종 제품 앱으로 배포하지 않는다.

---

## 2. 현재 상태와 핵심 판단

### 2.1 `adb-tunneling`에서 확인된 기능

현재 Phase 0 구현에는 다음 기능이 있다.

- `_adb-tls-connect._tcp.`와 `_adb-tls-pairing._tcp.` 독립 검색
- NSD 서비스 resolve 및 동적 포트 취득
- 발견 주소가 현재 Android 기기의 Wi-Fi 주소인지 판정
- payload를 보내지 않는 제한 시간 TCP connect/close 검사
- 동일 종류의 자기 기기 후보가 여러 개이면 자동 선택 거부
- SSH 서버의 `127.0.0.1:5555`, `127.0.0.1:5556`에 역방향 포워딩 생성
- 무선 디버깅 및 pairing 화면 수명주기에 따른 endpoint 변경 감지

실기기에서 자기 기기의 connect/pairing 광고 발견과 TCP 도달성, pairing 포트 재생성은 확인되었다. 다만 원격 `adb pair`, `adb connect`, `adb shell`, 화면 OFF 유지, Wi-Fi 재연결은 제품 통합 전 또는 통합 초기에 반드시 추가 검증해야 한다.

### 2.2 `ssh_tunneling`의 현재 구조

현재 앱은 이미 Jetpack Compose와 Material 3를 사용한다. 따라서 이번 UI 작업은 View 기반 UI를 Compose로 옮기는 작업이 아니라 다음과 같은 구조적 개편이다.

- 하나의 긴 화면에 대시보드, 호스트 설정, 포워딩 설정이 모두 배치된 구조 제거
- 선택된 항목을 즉시 저장하는 편집 방식에서 명확한 생성/편집/저장 흐름으로 변경
- 기능 중심 화면에서 사용자 작업 중심 화면으로 변경
- 상태 문구 위주의 피드백을 시각적 상태, 단계 안내, 복구 동작으로 변경
- compact/medium/expanded 크기에 맞는 적응형 navigation 및 list-detail 적용

SSH 구현은 현재 Android에 로컬 listener를 여는 `setPortForwardingL`만 지원한다. ADB 통합에는 SSH 서버에 listener를 여는 `setPortForwardingR` 지원이 추가되어야 한다. 따라서 자동 감지 포트를 기존 `remotePort`에 대입하는 것만으로는 기능이 완성되지 않는다.

---

## 3. 제품 원칙

1. **사용자 작업을 먼저 보여준다.** 첫 화면에서 설정 폼 대신 연결 상태와 주요 실행 버튼을 보여준다.
2. **ADB 전문 용어를 필요한 순간에만 노출한다.** 기본 화면에서는 “무선 디버깅”과 “페어링”을 사용하고 mDNS, NSD, reverse forwarding은 상세 정보에서만 설명한다.
3. **상태마다 다음 행동을 제공한다.** 오류 문구만 보여주지 않고 권한 허용, 무선 디버깅 열기, pairing 화면 열기, 포트 변경 등의 해결 동작을 함께 제공한다.
4. **위험한 기본값을 허용하지 않는다.** ADB용 서버 listener는 `127.0.0.1`에만 바인딩하고 서버 host key 검증을 요구한다.
5. **기존 일반 터널과 설정을 보존한다.** 기존 JSON과 저장 데이터는 자동으로 일반 로컬 포워딩으로 마이그레이션한다.
6. **동적 상태와 사용자 설정을 분리한다.** 감지된 IP와 동적 포트는 영구 저장하지 않고 런타임 상태로만 유지한다.
7. **접근성과 적응형 레이아웃을 출시 조건으로 둔다.** 48dp 터치 영역, 스크린 리더 설명, 색상 외 상태 표현, 글꼴 배율, 세로/가로/대화면을 함께 검증한다.

---

## 4. 새 정보 구조

### 4.1 최상위 화면

앱의 최상위 목적지를 다음 세 개로 구성한다.

| 목적지 | 역할 | 주요 내용 |
|---|---|---|
| 홈 | 실행과 상태 확인 | 활성 터널, 빠른 연결, ADB 준비 상태, 최근 오류 |
| 터널 | 구성 관리 | 전체 터널 목록, 검색/필터, 생성, 편집, 삭제 |
| 설정 | 앱 전역 관리 | 위젯, 테마, 내보내기/불러오기, 업데이트, 앱 정보 |

휴대전화에서는 `NavigationBar`, medium/expanded 화면에서는 `NavigationRail`을 사용한다. expanded 화면의 터널 관리는 목록과 상세를 동시에 보여주는 list-detail 구조로 만든다.

### 4.2 호스트와 터널의 관계

SSH 호스트는 사용자가 매번 직접 편집하는 본문 카드가 아니라 재사용 가능한 연결 자격 증명으로 취급한다.

- 터널 생성 과정에서 기존 SSH 호스트 선택 또는 새 호스트 추가
- 호스트 관리 화면은 터널 화면의 보조 목적지 또는 설정 내 관리 메뉴로 제공
- 한 호스트를 여러 일반/ADB 터널이 공유 가능
- 호스트 삭제 시 영향을 받는 터널 수를 확인하는 확인 창 제공

### 4.3 터널 종류

사용자에게 다음 세 종류를 제공한다.

| 종류 | 사용자 표현 | 내부 방식 | 기본 서버 포트 |
|---|---|---|---|
| 일반 | 일반 SSH 터널 | Local forwarding | 해당 없음 |
| ADB 연결 | 무선 디버깅 | Remote forwarding + `_adb-tls-connect._tcp.` | 5555 |
| ADB 페어링 | 기기 페어링 | Remote forwarding + `_adb-tls-pairing._tcp.` | 5556 |

1차 버전에서는 임의의 정적 reverse forwarding 편집 UI를 제공하지 않는다. 내부 SSH 계층은 이후 확장 가능하게 만들되, 제품 화면에는 검증된 ADB 사용 사례만 노출한다.

---

## 5. 핵심 사용자 흐름

### 5.1 첫 실행

1. 앱의 역할을 한 문장으로 설명한다.
2. “일반 SSH 터널”과 “Android 무선 디버깅” 중 시작 목적을 선택한다.
3. Android 무선 디버깅을 선택한 경우에만 로컬 네트워크 권한의 이유를 설명하고 요청한다.
4. SSH 호스트를 등록한다.
5. 연결 테스트와 host key fingerprint 확인을 수행한다.
6. ADB 연결/페어링 터널을 권장 기본값으로 생성한다.
7. 홈으로 이동해 준비 상태와 다음 행동을 표시한다.

온보딩은 다시 볼 수 있고 건너뛸 수 있어야 한다. 앱 업데이트 후 기존 설정이 있는 사용자는 온보딩을 다시 거치지 않는다.

### 5.2 일반 SSH 터널 생성

1. 터널 화면의 FAB에서 “새 터널” 선택
2. “일반 SSH 터널” 선택
3. SSH 호스트 선택
4. 로컬 포트, 대상 host, 대상 port 입력
5. 입력 즉시 inline validation 표시
6. 저장 후 상세 화면에서 연결

고급값은 기본 폼에서 숨기고 확장 영역에 둔다. 포트 방향은 예시와 작은 다이어그램으로 `이 휴대전화:8080 → 대상 서버:80`처럼 표현한다.

### 5.3 ADB 빠른 설정

ADB는 별도 포트 입력 폼이 아니라 단계형 설정으로 제공한다.

1. SSH 호스트 선택
2. 로컬 네트워크 권한 확인
3. Android 개발자 옵션의 무선 디버깅 활성화 안내
4. connect endpoint 자동 검색 및 TCP 확인
5. 서버 listener 포트 확인, 기본값 5555
6. SSH 연결 및 reverse forwarding 생성
7. 서버에서 실행할 `adb connect 127.0.0.1:5555` 명령을 복사 가능하게 표시

페어링은 별도 카드 또는 같은 ADB 상세 화면의 “새 기기 페어링” 작업으로 제공한다.

1. 사용자에게 Android 설정의 “페어링 코드로 기기 페어링” 화면을 열도록 안내
2. pairing endpoint 발견 대기
3. 발견 즉시 서버 `127.0.0.1:5556` 포워딩 생성
4. `adb pair 127.0.0.1:5556` 명령 복사 제공
5. pairing 화면이 닫혀 endpoint가 사라지면 오류가 아닌 “페어링 대기 종료”로 표시하고 5556 listener 제거

앱은 pairing code를 읽거나 저장하거나 전송하지 않는다.

### 5.4 재연결과 복구

- Wi-Fi가 끊기면 stale reverse forwarding을 제거하고 “Wi-Fi 연결 대기”로 전환한다.
- connect endpoint가 잠시 사라지면 짧은 유예 후 listener를 제거한다.
- 새 동적 포트가 발견되면 사용자 조작 없이 forwarding target을 교체한다.
- 권한이 거부되면 시스템 설정 또는 권한 재요청 동작을 제공한다.
- 서버 포트 충돌은 SSH 인증 오류와 구분하고 서버 포트 변경 동작을 제공한다.
- pairing endpoint 소실은 정상 상태로 처리한다.

---

## 6. 화면별 UX 명세

### 6.1 홈

홈은 설정 편집 화면이 아니라 운영 대시보드다.

- 상단: 전체 상태 요약과 “모두 연결/모두 해제” 동작
- 활성 터널: 상태별 카드 목록
- ADB 카드: 무선 디버깅 준비 상태와 다음 행동
- 빠른 작업: 새 터널, ADB 설정, 위젯 구성
- 최근 문제: 해결되지 않은 오류만 간결하게 표시

터널 카드에는 다음만 우선 표시한다.

- 이름과 종류 아이콘
- 연결 대상 또는 ADB 서버 포트
- 상태 label과 상태 아이콘
- 명확한 연결/해제 버튼

감지된 IP나 내부 포트 등 진단용 정보는 펼침 상세에 둔다.

### 6.2 터널 목록

- 종류 및 상태 FilterChip
- 이름/호스트 검색
- 목록 항목 swipe 삭제는 사용하지 않고 overflow 메뉴와 확인 창 사용
- 항목 전체를 눌러 상세 진입
- trailing switch 대신 명시적인 연결/해제 아이콘 버튼 사용
- 빈 상태에서는 기능 설명과 첫 터널 생성 CTA 제공

### 6.3 터널 상세

상단에 상태와 주 동작을 배치하고 편집 폼은 그 아래 둔다.

- 연결 상태 hero 영역
- 연결/해제 primary action
- 현재 경로를 읽기 쉬운 문장이나 다이어그램으로 표시
- ADB는 감지 단계 timeline 표시
- 설정 편집은 명시적인 편집 모드로 진입
- 연결 중 변경 불가능한 값은 disabled 처리하고 이유 제공
- 삭제는 overflow 메뉴의 destructive action으로 배치

### 6.4 SSH 호스트 편집

- 표시 이름, 주소, SSH port, username
- password/private key 인증 선택에는 `SingleChoiceSegmentedButtonRow` 또는 접근 가능한 단일 선택 컴포넌트 사용
- 비밀값은 기본적으로 가리고 명시적 표시 버튼 제공
- private key는 문서 선택 또는 붙여넣기를 지원하고 전체 원문을 카드에 노출하지 않음
- host key fingerprint 확인 및 연결 테스트를 저장 전 단계에 포함
- 연결 테스트 결과를 성공/실패 색상만이 아니라 아이콘과 문구로 함께 표시

### 6.5 설정

- 위젯 슬롯 관리
- 동적 색상 사용 여부 및 시스템 테마/라이트/다크 선택
- 설정 내보내기/불러오기
- 업데이트 확인
- 앱 정보 및 오픈소스 라이선스
- 진단 정보 복사

비밀번호와 private key가 현재 설정 내보내기에 포함되는 동작은 별도로 재검토한다. 기본 내보내기에서는 secret을 제외하고, secret 포함 백업은 위험 안내와 별도 확인을 거쳐야 한다.

---

## 7. Material Design 3 및 Compose 설계 기준

### 7.1 디자인 시스템

- Android 12 이상에서는 dynamic color를 기본 사용
- dynamic color 비활성 또는 구형 Android를 위한 완성된 light/dark color scheme 정의
- 상태별 색상을 `ColorScheme` 위에 별도 semantic token으로 정의
- 기본 `Typography()`에 의존하지 않고 제목, 본문, label 계층을 명시
- shape, spacing, elevation 값을 공통 token으로 관리
- 영문 필드명과 한국어 설명이 혼재된 현재 문자열을 사용자 언어 기준으로 정리
- 모든 사용자 문자열을 `strings.xml`로 이동

### 7.2 Compose 구조

단일 `SshTunnelingApp.kt`에 화면과 다이얼로그를 계속 추가하지 않는다.

```text
ui/
├── App.kt
├── navigation/
│   ├── AppDestination.kt
│   └── AppNavHost.kt
├── home/
│   ├── HomeRoute.kt
│   ├── HomeScreen.kt
│   └── HomeViewModel.kt
├── tunnels/
│   ├── TunnelListRoute.kt
│   ├── TunnelListScreen.kt
│   ├── TunnelDetailRoute.kt
│   ├── TunnelDetailScreen.kt
│   ├── TunnelEditorScreen.kt
│   └── TunnelViewModel.kt
├── hosts/
│   ├── HostPickerSheet.kt
│   └── HostEditorScreen.kt
├── settings/
│   └── SettingsScreen.kt
├── adb/
│   ├── AdbSetupScreen.kt
│   ├── AdbDiscoveryStatus.kt
│   └── PairingGuideSheet.kt
├── components/
│   ├── TunnelCard.kt
│   ├── ConnectionStatusBadge.kt
│   ├── EmptyState.kt
│   └── PortPathDiagram.kt
└── theme/
    ├── Color.kt
    ├── Theme.kt
    ├── Type.kt
    └── Tokens.kt
```

- route composable은 ViewModel state 수집 및 event 연결만 담당한다.
- screen composable은 immutable state와 callback만 받는다.
- 일시적 입력 상태와 저장된 도메인 상태를 분리한다.
- 데이터 변경 때마다 즉시 영구 저장하지 않고 편집 완료 시 validation 후 저장한다.
- 긴 목록에는 `LazyColumn`을 사용하고 안정적인 key를 제공한다.
- 모든 주요 화면에 light/dark, compact/expanded, loading/error/empty Preview를 작성한다.

### 7.3 적응형 레이아웃

| 크기 | navigation | 터널 관리 화면 |
|---|---|---|
| Compact | 하단 NavigationBar | 목록과 상세를 별도 화면으로 이동 |
| Medium | NavigationRail | 목록 중심, 상세는 전체 pane 또는 지원 pane |
| Expanded | NavigationRail | 목록과 상세를 동시에 표시하는 list-detail |

가능하면 Material 3 adaptive/navigation suite 계열 API를 사용한다. 도입 API가 현재 Compose BOM과 호환되지 않으면 window size class 기반 자체 분기부터 적용하되 화면 state와 navigation 구조는 동일하게 유지한다.

### 7.4 접근성

- 모든 interactive icon에 구체적인 `contentDescription` 제공
- 장식 아이콘만 `null` 사용
- 모든 touch target 최소 48dp
- TalkBack 탐색 순서와 heading semantics 지정
- 상태를 색상만으로 구분하지 않고 아이콘과 문구 병행
- 글꼴 200%에서도 잘림이나 겹침이 없도록 확인
- 포트 입력에 숫자 키보드와 적절한 IME action 적용
- 오류를 필드와 연결하고 첫 오류로 포커스 이동
- 애니메이션은 시스템의 motion 감소 설정을 존중

---

## 8. 도메인 및 저장 모델 변경

### 8.1 포워딩 모드

```kotlin
enum class ForwardMode {
    LOCAL,
    ADB_CONNECT,
    ADB_PAIRING,
}
```

기존 `PortForwardRule`에는 최소한 다음 값을 추가한다.

```kotlin
val mode: ForwardMode = ForwardMode.LOCAL
val reverseBindHost: String = "127.0.0.1"
val reverseBindPort: Int = 5555
```

- `LOCAL`: 기존 `localPort`, `remoteHost`, `remotePort` 사용
- `ADB_CONNECT`: 자동 감지 endpoint를 local target으로 사용, 기본 reverse port 5555
- `ADB_PAIRING`: 자동 감지 endpoint를 local target으로 사용, 기본 reverse port 5556
- 감지된 address/port는 `PortForwardRule`에 저장하지 않음
- ADB 모드의 `reverseBindHost`는 1차 출시에서 `127.0.0.1` 고정

### 8.2 런타임 상태

현재 네 가지 연결 상태보다 세부 상태가 필요하다.

```kotlin
enum class TunnelPhase {
    IDLE,
    WAITING_FOR_PERMISSION,
    WAITING_FOR_WIFI,
    DISCOVERING_ADB,
    PROBING_ADB,
    CONNECTING_SSH,
    BINDING_FORWARD,
    CONNECTED,
    WAITING_FOR_PAIRING,
    RECONNECTING,
    ERROR,
}
```

UI는 내부 enum 이름을 직접 출력하지 않고 사용자 행동 중심 문구로 매핑한다. 런타임 IP와 port가 들어간 세부 상태는 메모리에만 유지하며, 프로세스 재시작용 저장 상태에는 민감정보를 제거한다.

### 8.3 저장 마이그레이션

- JSON schema version 추가
- version이 없으면 기존 v1로 간주
- 기존 모든 포워딩에 `mode=LOCAL` 적용
- 새 필드를 모르는 구버전 앱으로 다시 가져갈 수 없음을 export 화면에 안내
- import 전에 schema와 포트 범위 검증
- import 실패 시 기존 설정을 변경하지 않음
- 마이그레이션 및 import를 순수 함수로 분리해 fixture 테스트 작성

---

## 9. ADB 자동 감지 계층

제품 코드에는 다음 패키지를 추가한다.

```text
adb/
├── AdbServiceKind.kt
├── AdbEndpoint.kt
├── AdbEndpointState.kt
├── AdbEndpointDiscovery.kt
├── NetworkTracker.kt
└── AddressMatcher.kt
```

### 9.1 감지 규칙

- connect와 pairing을 독립적으로 지속 검색
- 현재 Wi-Fi `LinkProperties`의 주소와 일치하는 endpoint만 자기 기기로 간주
- port는 1~65535 범위만 허용
- 첫 주소를 무조건 택하지 말고 연결 가능한 주소를 probe해 선택
- TCP probe timeout은 2초, payload는 전송하지 않음
- 자기 기기 후보가 여러 개이면 임의 선택하지 않음
- service lost 또는 network lost 시 endpoint를 즉시 무효화
- 빠르게 반복되는 found/lost 이벤트에는 generation token과 짧은 debounce/grace 적용

### 9.2 수명주기

- ADB discovery는 Activity나 화면 ViewModel이 아니라 foreground service가 소유
- 여러 ADB 터널이 하나의 discovery instance를 공유
- 활성 ADB 터널이 하나 이상일 때만 multicast lock 유지
- 마지막 ADB 터널 종료 시 NSD listener, network callback, multicast lock 모두 해제
- callback과 service command의 동시 접근을 직렬화하여 stale 결과 반영 방지

### 9.3 권한과 지원 버전

- Android 11/API 30 미만에서는 ADB TLS 자동 감지 모드 비활성화
- manifest에 `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_LOCAL_NETWORK` 추가
- API 37 이상에서 로컬 네트워크 runtime permission 처리
- 권한 설명은 요청 전에 한 번 제공하되 거부 후 강제 반복 요청하지 않음
- 권한 거부가 일반 SSH 터널 기능을 막지 않도록 분리
- compile/target SDK 37 전환은 CI toolchain 및 배포 영향 확인 후 별도 커밋으로 수행

---

## 10. SSH 및 포워딩 계층 변경

### 10.1 `SshTunnelManager` 확장

기존 manager에 다음 역할을 명확히 분리한다.

```kotlin
connectSession()
addLocalForward(...)
addReverseForward(...)
replaceReverseTarget(...)
removeForward()
disconnectSession()
verifyConnected()
```

ADB reverse forwarding은 다음 형태다.

```kotlin
session.setPortForwardingR(
    "127.0.0.1",
    configuredServerPort,
    detectedAndroidAddress,
    detectedAndroidPort,
)
```

### 10.2 동적 포트 reconcile

foreground service는 설정으로부터 desired forwarding을 만들고 현재 SSH 상태와 비교하여 차이만 반영한다.

- 동일 endpoint 이벤트 재수신: 아무 작업도 하지 않음
- endpoint port 변경: 기존 remote forwarding 제거 후 새 target 등록
- endpoint lost: remote forwarding 제거, SSH 세션은 재사용 가능하면 유지
- SSH session lost: endpoint가 유효하면 backoff 후 재접속
- 포트 bind 실패: 다른 터널에 영향 없이 해당 터널만 오류 처리
- 교체 중 실패: stale listener를 남기지 않고 명시적 오류 또는 재연결 상태로 전환

1차 구현은 현재 구조와 같이 포워딩 하나당 SSH 세션 하나를 유지한다. 다수 포워딩의 세션 공유는 동시성, 부분 실패, 인증 갱신 범위가 커지므로 별도 최적화로 남긴다.

### 10.3 서버 키 검증

현재 앱의 `StrictHostKeyChecking=no`는 ADB reverse forwarding 출시 전에 제거해야 한다.

- SSH 호스트 프로필에 SHA-256 fingerprint 저장
- 최초 연결 테스트에서 관측 fingerprint를 사용자에게 비교/확인
- 저장된 fingerprint 불일치 시 연결 차단
- ADB 터널은 fingerprint가 확인되지 않은 호스트에서 실행 불가
- 일반 기존 터널의 마이그레이션 UX는 “호스트 확인 필요” 상태로 안내

---

## 11. 서비스, 상태 및 알림

### 11.1 Foreground service coordinator

`TunnelForegroundService`가 직접 모든 세부 구현을 가지지 않도록 다음으로 분리한다.

```text
service/
├── TunnelForegroundService.kt
├── TunnelCoordinator.kt
├── TunnelSession.kt
├── TunnelStatusStore.kt
└── SshTunnelManager.kt
```

- Service: Android lifecycle, command, notification만 담당
- Coordinator: desired/actual state reconcile
- TunnelSession: 터널 한 개의 실행 상태와 job 관리
- Discovery: ADB endpoint 상태 제공
- StatusStore: UI와 widget에 state flow 제공

`START_STICKY` 재시작 시 사용자가 이전에 켜 둔 터널 정책을 명확히 정의한다. 1차 권장은 서비스가 비정상 재생성되었을 때 이전 활성 목록을 복원하되, 권한·네트워크·endpoint를 다시 검증한 뒤 연결하는 것이다.

### 11.2 알림

- 활성/연결 중/문제 터널 개수 표시
- ADB 탐색 중이면 그 상태를 별도 표시
- “모두 해제” action 유지
- 오류 발생 시 일반 진행 알림과 구분하되 과도한 반복 알림 금지
- notification channel과 문구를 한국어 UX에 맞게 정리

---

## 12. 위젯 개편

기존 6개 고정 슬롯의 장점은 유지하되 설정과 표현을 단순화한다.

- 위젯 슬롯 배치는 설정 화면에서 전용 목록으로 관리
- 각 셀에 종류 아이콘, 이름, 연결 상태 표시
- ADB 터널은 “검색 중”, “연결됨”, “조치 필요”를 구분
- 토글 직후 optimistic success로 보이지 않게 진행 상태 표시
- 빈 슬롯을 누르면 앱의 위젯 구성 화면으로 이동
- TalkBack용 상태와 동작 설명 제공

---

## 13. 보안 및 개인정보

- ADB reverse bind는 `127.0.0.1` 고정
- 서버 문서에 `GatewayPorts no` 및 `PermitListen` 예시 제공
- password, private key, pairing code, 전체 IP를 로그와 crash report에서 제외
- 진단 정보 복사 시 IPv4 tail 및 IPv6 전체 마스킹
- runtime status 저장 시 endpoint 주소 제거
- pairing code를 읽거나 자동 입력하려는 기능은 구현하지 않음
- secret 없는 설정 export를 기본으로 제공
- 앱 백업 정책에서 secret 저장소 제외 여부 확인
- 포트 충돌, 서버 정책 거부, 인증 실패, fingerprint 불일치를 서로 다른 오류로 분류

---

## 14. 구현 단계와 커밋 계획

### Phase 0 — 통합 전 타당성 게이트

- 기존 probe 앱에서 원격 `adb pair`, `adb connect`, `adb shell` 실행
- connect 포트 변경, 화면 OFF, Wi-Fi 재연결 확인
- 결과를 `adb-tunneling/docs/phase-0-feasibility.md`에 기록

통과 기준: 최소 한 기기에서 end-to-end ADB 성공, 목표 기기군에서 self-discovery 성공. 실패 시 제품 통합을 중단하는 대신 manual endpoint fallback 범위를 먼저 결정한다.

### Phase 1 — 도메인 모델과 마이그레이션

- `ForwardMode`, 세부 runtime phase 추가
- JSON schema version과 v1 마이그레이션 구현
- import validation 및 rollback 보장
- 단위 테스트 작성

권장 커밋: `feat: add forwarding modes and settings migration`

### Phase 2 — ADB discovery 제품화

- prototype의 discovery/network/address 판정 로직 이식
- callback 기반 구현을 `StateFlow` 상태로 정리
- 공유 수명주기와 중복 후보 처리 구현
- JVM 테스트 가능한 판정/reducer 분리

권장 커밋: `feat: add lifecycle-aware adb endpoint discovery`

### Phase 3 — SSH reverse forwarding과 보안

- local/reverse forwarding API 분리
- endpoint 교체 및 제거 구현
- host key fingerprint 검증 도입
- 오류 분류 추가

권장 커밋: `feat: support secure adb reverse forwarding`

### Phase 4 — 서비스 coordinator

- service에서 coordinator/session 분리
- discovery와 SSH desired-state reconcile
- reconnect/backoff, Wi-Fi 변경, stale forwarding 제거
- 알림 및 widget 상태 연결

권장 커밋: `refactor: reconcile dynamic tunnel sessions in foreground service`

### Phase 5 — Compose navigation 및 디자인 시스템

- Navigation Compose 도입
- 홈/터널/설정 목적지 분리
- theme token, typography, 문자열 정리
- 공통 상태 컴포넌트와 적응형 app shell 구현

권장 커밋: `feat: introduce adaptive material 3 app architecture`

### Phase 6 — 핵심 화면 재설계

- 홈 대시보드
- 터널 목록/list-detail
- 일반 터널 생성 및 편집
- SSH 호스트 선택/편집
- 빈 상태, 로딩, 오류, confirmation UX

권장 커밋: `feat: redesign tunnel management experience`

### Phase 7 — ADB 전용 UX

- ADB 단계형 설정
- discovery timeline 및 복구 action
- pairing guide bottom sheet
- 명령 복사 및 서버 port 변경
- 권한 요청/거부 UX

권장 커밋: `feat: add guided wireless debugging experience`

### Phase 8 — 위젯, 설정, 진단 정리

- 위젯 관리 화면과 상태 표현 개편
- 안전한 내보내기/불러오기
- 업데이트/앱 정보 이동
- 마스킹된 진단 정보 복사

권장 커밋: `feat: polish widgets settings and diagnostics`

### Phase 9 — 검증과 출시 준비

- 전체 테스트와 수동 기기 매트릭스 실행
- 접근성 및 대화면 검증
- README와 SSH 서버 설정 문서 갱신
- 기존 사용자 마이그레이션 release candidate 검증

권장 커밋: `test: validate adb tunneling and adaptive ui release`

---

## 15. 파일별 예상 변경

### 기존 파일

- `app/build.gradle.kts`: Navigation Compose, 필요 시 adaptive dependency, SDK 설정
- `app/src/main/AndroidManifest.xml`: 네트워크/멀티캐스트/로컬 네트워크 권한
- `model/TunnelModels.kt`: mode, runtime phase, host fingerprint
- `data/TunnelPreferences.kt`: schema version, 마이그레이션, 안전한 export
- `service/SshTunnelManager.kt`: local/reverse forwarding 및 host key 검증
- `service/TunnelForegroundService.kt`: coordinator 위임 및 notification
- `service/TunnelRuntime.kt`: 구조화된 runtime state
- `ui/TunnelViewModel.kt`: 화면별 ViewModel로 분해
- `ui/SshTunnelingApp.kt`: 단일 화면 제거 후 app shell로 대체
- `ui/theme/*`: 완성된 Material 3 color/typography/token
- `widget/*`: 새 상태 모델과 설정 UX 반영
- `res/values/strings.xml`: 모든 UI 문자열 리소스화 및 용어 통일
- `README.md`: ADB 사용법, 서버 보안 설정, 지원 버전

### 새 파일군

- `adb/*`: endpoint discovery
- `service/TunnelCoordinator.kt`
- `service/TunnelSession.kt`
- `ui/navigation/*`
- `ui/home/*`
- `ui/tunnels/*`
- `ui/hosts/*`
- `ui/adb/*`
- `ui/settings/*`
- `ui/components/*`
- 마이그레이션, reducer, address matcher, UI state 테스트

---

## 16. 테스트 전략

### 16.1 단위 테스트

- IPv4/IPv6 자기 기기 주소 판정
- endpoint 없음/하나/여러 개 선택
- port 0 및 범위 초과 거부
- TCP 실패 endpoint 제외
- endpoint generation 변경 시 stale probe 결과 무시
- desired/actual forwarding reducer의 idempotency
- endpoint 변경 시 remove/add 순서
- pairing lost의 정상 대기 상태 전환
- 기존 JSON의 `LOCAL` 마이그레이션
- 잘못된 import가 기존 설정을 보존
- secret 없는 export
- runtime phase에서 사용자 상태 문구 매핑

### 16.2 UI 테스트

- 첫 실행에서 일반/ADB 경로 분기
- 권한 허용/거부/영구 거부
- compact navigation과 expanded list-detail
- 일반 터널 생성 validation
- ADB 검색 중/발견/중복/실패/연결 상태
- pairing guide의 정상 종료
- 연결 중 설정 잠금
- host 삭제 영향 확인 dialog
- font scale 200% 및 긴 번역 문자열
- screen reader semantics와 touch target

### 16.3 실기기 및 통합 테스트

- Android 11 이상 Pixel 1대
- Android 11 이상 비-Pixel OEM 1대 이상
- 가능하면 API 37 로컬 네트워크 권한 기기/에뮬레이터
- IPv4 및 IPv6/link-local 주소 환경
- 화면 OFF 10분 및 Doze
- Wi-Fi 끊기/재연결과 네트워크 전환
- 무선 디버깅 토글 후 동적 포트 교체
- pairing 화면 반복 열기/닫기
- 서버 5555/5556 포트 충돌
- SSH 인증 실패 및 host key 변경
- process kill 및 foreground service 재생성
- 기존 설치본에서 업데이트 후 데이터 보존

저장소의 `AGENTS.md` 지침에 따라 로컬 Gradle 명령은 실행하지 않는다. 컴파일, lint, unit test, screenshot/UI test는 GitHub Actions에서 수행하고, 로컬에서는 정적 검토와 비-Gradle 도구만 사용한다.

---

## 17. 출시 완료 기준

다음 조건을 모두 만족해야 통합 버전을 출시할 수 있다.

### 기능

- 기존 일반 SSH local forwarding이 이전 설정 그대로 동작
- connect 및 pairing 포트를 사용자 입력 없이 자동 감지
- 서버 loopback의 5555/5556을 통한 `adb pair/connect/shell` 성공
- 동적 포트 변경 시 앱 재실행 없이 자동 교체
- endpoint 또는 Wi-Fi 소실 시 stale server listener 제거
- 화면 OFF와 Activity 종료 후 foreground service에서 유지

### UX

- 첫 실행 사용자가 내부 포워딩 방향을 몰라도 ADB 연결 설정 가능
- 각 실패 상태에 원인과 다음 행동이 함께 표시됨
- compact, medium, expanded 화면에서 핵심 기능 접근 가능
- TalkBack, 200% 글꼴, light/dark/dynamic color 검증 완료
- destructive action에 확인 절차가 있고 진행 중 상태가 명확함

### 보안과 호환성

- ADB listener가 외부 인터페이스에 바인딩되지 않음
- SSH host key 불일치 시 연결 차단
- 로그와 기본 export에 secret 및 전체 endpoint 주소가 없음
- 기존 JSON 및 설치 데이터 마이그레이션 테스트 통과
- 서버 포트 충돌과 인증/네트워크 오류가 구분됨

---

## 18. 제외 범위와 후속 후보

이번 통합에서 제외한다.

- pairing code 자동 읽기 또는 자동 입력
- root/Shizuku를 이용한 포트 조회
- 여러 SSH 포워딩의 단일 session 공유
- 인터넷 전체에 ADB 포트 공개
- 임의 static reverse forwarding용 고급 편집 UI
- 데스크톱 companion 앱

자동 감지가 주요 OEM에서 안정적이지 않을 경우 후속 우선순위는 다음과 같다.

1. 무선 디버깅 화면의 포트를 직접 입력하는 manual fallback
2. 선택적 Shizuku 기반 조회
3. 별도 companion 장치 또는 데스크톱 지원

---

## 19. 구현 시작 전 확정할 기본값

별도 제품 결정이 없으면 다음을 기본값으로 사용한다.

- ADB connect 서버 port: 5555
- ADB pairing 서버 port: 5556
- reverse bind address: `127.0.0.1` 고정
- 포트 변경 허용, bind address 변경 불가
- 재부팅 자동 시작: OFF
- 동적 색상: ON
- secret 없는 설정 export: 기본
- ADB 모드의 SSH host fingerprint 확인: 필수
- pairing endpoint 소실: 오류가 아닌 대기/종료 상태
- 자동 감지 실패 시 manual endpoint 입력: 1차 출시 이후 기능 플래그로 검토

