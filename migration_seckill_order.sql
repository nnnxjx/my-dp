-- Existing databases: check for duplicates before applying this index.
SELECT user_id, voucher_id, COUNT(*) AS count
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;

ALTER TABLE tb_voucher_order
ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
