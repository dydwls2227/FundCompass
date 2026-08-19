-- evidence가 원문에 없는 조건은 모델의 진술 외에 아무 근거가 없다.
-- F4가 그 값으로 부적격 판정을 내리면 자격이 되는 사업자를 탈락시킨다.
--
-- extraction(모델 원본)은 그대로 두고 신뢰할 수 없는 필드 이름만 따로 적는다.
-- 원본을 덮어쓰면 프롬프트를 고칠 때 "모델이 실제로 뭐라 했는지"를 잃는다.
ALTER TABLE program_eligibility
    ADD COLUMN unverified_fields VARCHAR(200);

COMMENT ON COLUMN program_eligibility.unverified_fields
    IS 'evidence 대조에 실패한 필드 이름(쉼표 구분). F4는 이 필드를 UNKNOWN으로 취급한다';
