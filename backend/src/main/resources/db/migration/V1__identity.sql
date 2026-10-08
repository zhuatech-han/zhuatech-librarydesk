-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

-- 仅初始化结构；业务数据通过真实操作录入。
create table access_role (id bigint auto_increment primary key, name varchar(120) not null, scope varchar(20) not null, version bigint not null);

create table permission (id bigint auto_increment primary key, code varchar(60) not null unique, name varchar(120) not null);

create table nav_menu (id bigint auto_increment primary key, code varchar(60) not null unique, name varchar(120) not null, name_en varchar(120) not null, permission_code varchar(60) not null, position int not null, enabled boolean not null);

create table system_setting (id bigint auto_increment primary key, code varchar(60) not null unique, parameter_value varchar(6000) not null);

create table dictionary_entry (id bigint auto_increment primary key, type varchar(60) not null, code varchar(60) not null, name varchar(120) not null, name_en varchar(120) not null, enabled boolean not null, unique(type,code));

create table audit_event (id bigint auto_increment primary key, actor varchar(80) not null, action varchar(120) not null, object_id varchar(80) not null, department_id bigint not null, created_at timestamp(6) not null);

create table role_permission (role_id bigint not null,permission_code varchar(60) not null,primary key(role_id,permission_code),foreign key(role_id) references access_role(id),foreign key(permission_code) references permission(code));

create table department (
 id bigint auto_increment primary key,
 name varchar(120) not null,
 zone varchar(80) not null,
 enabled boolean not null,
 version bigint not null
);

create table account (id bigint auto_increment primary key, username varchar(60) not null unique, display_name varchar(120) not null, password_hash varchar(100) not null, role_id bigint not null, department_id bigint not null, enabled boolean not null, version bigint not null, foreign key(role_id) references access_role(id), foreign key(department_id) references department(id));
