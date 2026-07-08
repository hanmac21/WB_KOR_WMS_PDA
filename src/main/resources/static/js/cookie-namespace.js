/*
26.07.08 SJ 작성
[작성 의도]
다중 프로젝트가 같은 브라우저에서 실행될 때, 쿠키의 이름이 중복되어서 덮어씌워지는 상황을 방지하고자 함.

[로직 설명]
document.cookie에 대한 읽기/쓰기 로직을 커스터마이즈하여,
POL_WEB_COOKIENAME처럼 쿠키 앞에 프로젝트별 접두사를 붙여서 쿠키를 저장하고,
읽을 때는 접두사를 제거하여 처리한다.

[사용시 주의 사항]
- JS 파일 중(특히 쿠키와 관련된 작업을 하는 JS)에 가장 먼저 import 되어야 함. (예시 login.jsp, w_main.jsp 상단)
<script src="/resources/js/cookie-namespace.js"></script>
*/

(function () {
    var PREFIX = "KOR_PDA_";

    var descriptor = Object.getOwnPropertyDescriptor(Document.prototype, "cookie") ||
                     Object.getOwnPropertyDescriptor(HTMLDocument.prototype, "cookie");

    if (!descriptor || !descriptor.get || !descriptor.set) return;

    var originalGet = descriptor.get;
    var originalSet = descriptor.set;

    Object.defineProperty(document, "cookie", {
        configurable: true, enumerable: true,

        get: function () {
            var raw = originalGet.call(document);
            if (!raw) return raw;

            var cookies = raw.split("; ");
            var result = [];

            for (var i = 0; i < cookies.length; i++) {
                var cookie = cookies[i];
                var eqIdx = cookie.indexOf("=");
                if (eqIdx < 0) continue;

                var name = cookie.substring(0, eqIdx).trim();

                // 1. 오직 내 접두사가 붙은 쿠키만 찾아서, 접두사를 떼고 반환
                if (name.indexOf(PREFIX) === 0) {
                    result.push(name.substring(PREFIX.length) + cookie.substring(eqIdx));
                }
            }
            return result.join("; ");
        },

        set: function (value) {
            if (typeof value !== "string") {
                originalSet.call(document, value);
                return;
            }

            var eqIdx = value.indexOf("=");
            if (eqIdx < 0) {
                originalSet.call(document, value);
                return;
            }

            var name = value.substring(0, eqIdx).trim();

            // 2. 이미 접두사가 있거나, 브라우저 보안 태그(__)가 아니라면 무조건 내 접두사 부착
            if (name.indexOf(PREFIX) !== 0 && name.indexOf("__") !== 0) {
                value = PREFIX + name + value.substring(eqIdx);
            }

            originalSet.call(document, value);
        }
    });
})();