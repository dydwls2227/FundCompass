ALTER TABLE program
    ADD COLUMN attachment_url  VARCHAR(500),
    ADD COLUMN print_file_name VARCHAR(500),
    ADD COLUMN print_file_url  VARCHAR(500);

COMMENT ON COLUMN program.attachment_url  IS '원본 첨부파일 다운로드 URL (주로 HWP)';
COMMENT ON COLUMN program.print_file_name IS 'PDF 변환본 파일명';
COMMENT ON COLUMN program.print_file_url  IS 'PDF 변환본 다운로드 URL — 자격요건 추출에 사용';