-- RemoteUpdate MariaDB 스키마 (참고용)
-- JPA 엔티티에서 Hibernate(MariaDBDialect)가 생성한 DDL. 엔티티를 바꾸면 다시 생성해야 함.
-- 개발 중에는 ddl-auto=update 로 자동 반영되며, 운영 전환 시 이 파일을 기준으로 마이그레이션 스크립트를 만든다.


    create table allowed_ip (
        enabled bit not null,
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        ip_or_cidr varchar(50) not null,
        description varchar(200),
        primary key (id)
    ) engine=InnoDB;

    create table app_user (
        enabled bit not null,
        failed_login_count integer not null,
        must_change_password bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        locked_until datetime(6),
        updated_at datetime(6) not null,
        role varchar(20) not null check ((role in ('STAFF','DEVELOPER','ADMIN'))),
        name varchar(100) not null,
        password_hash varchar(100) not null,
        email varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table download_history (
        downloaded_at datetime(6) not null,
        id bigint not null auto_increment,
        update_id bigint not null,
        user_id bigint not null,
        client_ip varchar(45) not null,
        downloader_name varchar(100) not null,
        primary key (id)
    ) engine=InnoDB;

    create table project (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        name varchar(200) not null,
        description varchar(2000),
        primary key (id)
    ) engine=InnoDB;

    create table setup_guide (
        created_at datetime(6) not null,
        created_by bigint not null,
        file_size bigint,
        id bigint not null auto_increment,
        project_id bigint not null,
        updated_at datetime(6) not null,
        title varchar(200) not null,
        file_key varchar(500),
        content mediumtext,
        file_name varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table update_history (
        changed_at datetime(6) not null,
        changed_by bigint not null,
        id bigint not null auto_increment,
        update_id bigint not null,
        action varchar(10) not null check ((action in ('CREATE','UPDATE','DISABLE','ENABLE'))),
        after_json longtext,
        before_json longtext,
        primary key (id)
    ) engine=InnoDB;

    create table update_info (
        created_at datetime(6) not null,
        developer_id bigint not null,
        file_size bigint not null,
        id bigint not null auto_increment,
        project_id bigint not null,
        updated_at datetime(6) not null,
        status varchar(10) not null check ((status in ('ACTIVE','DISABLED'))),
        version varchar(50) not null,
        checksum char(64) not null,
        title varchar(200) not null,
        file_key varchar(500) not null,
        content varchar(2000),
        file_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    alter table if exists allowed_ip 
       add constraint UK65t8ls9qhg6c6caagcsjjeaub unique (ip_or_cidr);

    alter table if exists app_user 
       add constraint UK1j9d9a06i600gd43uu3km82jw unique (email);

    create index idx_download_history_update_downloaded 
       on download_history (update_id, downloaded_at);

    create index idx_download_history_downloaded 
       on download_history (downloaded_at);

    create index idx_download_history_user 
       on download_history (user_id);

    alter table if exists project 
       add constraint UK3k75vvu7mevyvvb5may5lj8k7 unique (name);

    create index idx_update_history_update_changed 
       on update_history (update_id, changed_at);

    create index idx_update_info_project_status_created 
       on update_info (project_id, status, created_at);

    alter table if exists update_info 
       add constraint uk_update_info_project_version unique (project_id, version);

    alter table if exists allowed_ip 
       add constraint FKoes8oa3e3ub8qyhb2j61h4p0q 
       foreign key (created_by) 
       references app_user (id);

    alter table if exists download_history 
       add constraint FK42j4d1lpyeqiv7vf36iqxu271 
       foreign key (update_id) 
       references update_info (id);

    alter table if exists download_history 
       add constraint FK3pop9pakq5v5mlhrkmcr8f7sy 
       foreign key (user_id) 
       references app_user (id);

    alter table if exists setup_guide 
       add constraint FKigvgesc0dt4tbrxunvkos6kqy 
       foreign key (created_by) 
       references app_user (id);

    alter table if exists setup_guide 
       add constraint FKaxah7hml1s7mgkaxal40x9eej 
       foreign key (project_id) 
       references project (id);

    alter table if exists update_history 
       add constraint FKsif7uq9wlm9mms4t2l0457tc 
       foreign key (changed_by) 
       references app_user (id);

    alter table if exists update_history 
       add constraint FKjvyncd4lt4ynggn5dn4e09t9n 
       foreign key (update_id) 
       references update_info (id);

    alter table if exists update_info 
       add constraint FKbd1ca1nw04nhyj5wt2et8b6bm 
       foreign key (developer_id) 
       references app_user (id);

    alter table if exists update_info 
       add constraint FK6klrjsrcmfrw1l4leh8y1dk9i 
       foreign key (project_id) 
       references project (id);
