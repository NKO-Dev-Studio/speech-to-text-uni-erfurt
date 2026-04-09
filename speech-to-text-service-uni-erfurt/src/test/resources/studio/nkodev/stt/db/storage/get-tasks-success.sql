INSERT INTO speech_to_text_task (id, state, engine_type, model_identifier, output_format, locale, created_at, changed_at)
VALUES (21, 'WAITING_FOR_AUDIO', 'WHISPER_LOCAL', 'tiny', 'JSON', 'de', 1710000200000, NULL);
INSERT INTO speech_to_text_task (id, state, engine_type, model_identifier, output_format, locale, created_at, changed_at)
VALUES (22, 'PENDING', 'WHISPER_LOCAL', 'base', 'TXT', 'en-US', 1710000300000, 1710000400000);
INSERT INTO speech_to_text_task (id, state, engine_type, model_identifier, output_format, locale, created_at, changed_at)
VALUES (23, 'COMPLETED', 'WHISPER_LOCAL', 'large-v3', 'SRT', NULL, 1710000500000, 1710000600000);
