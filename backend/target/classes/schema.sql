drop table if exists order_items;
drop table if exists customers;

create table customers (
  id bigserial primary key,
  name varchar(255) not null
);

create table order_items (
  id bigserial primary key,
  customer_id bigint references customers(id),
  product varchar(255) not null,
  quantity int not null
);

create index if not exists idx_order_items_customer on order_items(customer_id);


