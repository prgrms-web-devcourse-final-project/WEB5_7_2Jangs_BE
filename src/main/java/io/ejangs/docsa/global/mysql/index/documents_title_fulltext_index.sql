CREATE FULLTEXT INDEX idx_title_ngram
    ON documents (title)
    WITH PARSER ngram;