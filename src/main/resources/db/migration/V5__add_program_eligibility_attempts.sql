-- FAILED 행이 남으면 findTargets()에서 영구 제외돼 그 공고는 다시는 추출되지 않았다.
-- 일시적 오류(네트워크·5xx)와 데이터 오류를 구분하지 못하는 것이 원인이다.
-- 시도 횟수를 세어 상한까지는 다시 도전하고, 넘으면 포기한다.
ALTER TABLE program_eligibility
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN program_eligibility.attempts
    IS 'FAILED 행의 누적 시도 횟수. 상한 미만이면 다음 배치에서 재시도한다';
