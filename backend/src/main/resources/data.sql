-- sample customers
insert into customers(name) select 'Customer ' || i from generate_series(1, 1000) as s(i);

-- sample order items: ~5000 rows
insert into order_items(customer_id, product, quantity)
select ((random()*999)::int + 1) as customer_id,
       'Product ' || ((random()*50)::int + 1),
       ((random()*10)::int + 1)
from generate_series(1, 5000);


