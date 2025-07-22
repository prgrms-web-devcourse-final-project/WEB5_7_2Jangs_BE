CREATE FULLTEXT INDEX idx_title_ngram
    ON docs (title)
    WITH PARSER ngram;