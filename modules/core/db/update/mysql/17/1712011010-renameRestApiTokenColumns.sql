alter table SYS_REST_API_TOKEN change ACCESS_TOKEN_VALUE TOKEN_VALUE varchar(255)^
alter table SYS_REST_API_TOKEN change ACCESS_TOKEN_BYTES TOKEN_BYTES longblob^
alter table SYS_REST_API_TOKEN drop CREATED_BY^