-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 图书流通结构；首次不创建虚构书目、读者或借阅数据。
create table book_title (
 id bigint auto_increment primary key,
 department_id bigint not null,
 title varchar(200) not null,
 author varchar(160) not null,
 isbn varchar(20) not null,
 category varchar(60) not null,
 language varchar(40) not null,
 description varchar(2000) not null,
 active boolean not null,
 created_at timestamp(6) not null,
 version bigint not null,
 foreign key (department_id) references department(id)
);

create table book_copy (
 id bigint auto_increment primary key,
 book_id bigint not null,
 barcode varchar(40) not null,
 shelf varchar(80) not null,
 status varchar(20) not null,
 note varchar(500) not null,
 created_at timestamp(6) not null,
 version bigint not null,
 foreign key (book_id) references book_title(id),
 unique(barcode)
);

create table patron (
 id bigint auto_increment primary key,
 account_id bigint not null,
 department_id bigint not null,
 card_no varchar(40) not null,
 active boolean not null,
 note varchar(500) not null,
 created_at timestamp(6) not null,
 version bigint not null,
 foreign key (department_id) references department(id),
 foreign key (account_id) references account(id),
 unique(card_no),
 unique(account_id)
);

create table book_loan (
 id bigint auto_increment primary key,
 copy_id bigint not null,
 patron_id bigint not null,
 department_id bigint not null,
 status varchar(20) not null,
 loan_date date not null,
 due_date date not null,
 loan_days int not null,
 max_renewals int not null,
 renewals int not null,
 created_at timestamp(6) not null,
 returned_at timestamp(6),
 return_condition varchar(20) not null,
 note varchar(500) not null,
 version bigint not null,
 foreign key (department_id) references department(id),
 foreign key (copy_id) references book_copy(id),
 foreign key (patron_id) references patron(id)
);

create table book_hold (
 id bigint auto_increment primary key,
 book_id bigint not null,
 patron_id bigint not null,
 department_id bigint not null,
 copy_id bigint,
 status varchar(20) not null,
 created_at timestamp(6) not null,
 ready_at timestamp(6),
 expires_at timestamp(6),
 closed_at timestamp(6),
 reason varchar(1000) not null,
 version bigint not null,
 foreign key (department_id) references department(id),
 foreign key (book_id) references book_title(id),
 foreign key (copy_id) references book_copy(id),
 foreign key (patron_id) references patron(id)
);

create table circulation_event (
 id bigint auto_increment primary key,
 department_id bigint not null,
 book_id bigint,
 copy_id bigint,
 patron_id bigint,
 loan_id bigint,
 hold_id bigint,
 action varchar(40) not null,
 note varchar(1000) not null,
 actor_id bigint not null,
 created_at timestamp(6) not null,
 foreign key (department_id) references department(id),
 foreign key (book_id) references book_title(id),
 foreign key (copy_id) references book_copy(id),
 foreign key (patron_id) references patron(id),
 foreign key (loan_id) references book_loan(id),
 foreign key (hold_id) references book_hold(id),
 foreign key (actor_id) references account(id)
);

create index idx_book_department on book_title(department_id,active);
create index idx_copy_book on book_copy(book_id,status);
create index idx_loan_patron on book_loan(patron_id,status);
create index idx_loan_copy on book_loan(copy_id,status);
create index idx_hold_queue on book_hold(book_id,status,created_at,id);
create index idx_hold_patron on book_hold(patron_id,status);
