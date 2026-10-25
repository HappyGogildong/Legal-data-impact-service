-- 소관부처(ministry)에 부처 코드가 섞여 저장된 행을 1회 정리한다.
--
-- 배경: 법령 API 는 소관부처를 {"content": "국토교통부", "소관부처코드": "1613000"} 로 중첩해 보내는데,
--       Normalizer 가 이를 평탄화하면서 "국토교통부\n1613000" 처럼 이름과 코드를 줄바꿈으로 이어 저장했다.
--       Normalizer 는 이제 content(부처명)만 저장한다.
-- 왜 마이그레이션인가: ministry 는 revision 계산에 들어가지 않는 행정 메타데이터라, 재수집해도
--       revision 이 같으면 배치가 저장을 건너뛰어 기존 행이 고쳐지지 않는다.
-- revision 은 일부러 건드리지 않는다(내용이 바뀐 것이 아니므로).
UPDATE law_versions
SET payload = jsonb_set(payload, '{ministry}', to_jsonb(split_part(payload->>'ministry', E'\n', 1)))
WHERE payload->>'ministry' LIKE E'%\n%';
