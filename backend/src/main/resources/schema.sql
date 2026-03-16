alter table if exists invoices
    add column if not exists quantity numeric(12,2),
    add column if not exists unit_price numeric(12,2),
    add column if not exists subtotal_amount numeric(12,2),
    add column if not exists tax_amount numeric(12,2),
    add column if not exists currency varchar(8),
    add column if not exists due_date date,
    add column if not exists payment_terms varchar(255),
    add column if not exists invoice_description text,
    add column if not exists parse_confidence numeric(3,2),
    add column if not exists raw_extracted_text text,
    add column if not exists parse_status varchar(255),
    add column if not exists needs_review boolean,
    add column if not exists processing_status varchar(255),
    add column if not exists duplicate_flag boolean,
    add column if not exists duplicate_reason varchar(255),
    add column if not exists extraction_error text,
    add column if not exists updated_at timestamp without time zone;

alter table if exists invoices
    drop constraint if exists ukl1x55mfsay7co0r3m9ynvipd5;

update invoices
set parse_status = 'SUCCESS'
where parse_status is null;

update invoices
set needs_review = false
where needs_review is null;

update invoices
set processing_status = 'SUCCESS'
where processing_status is null;

update invoices
set duplicate_flag = false
where duplicate_flag is null;

update invoices
set updated_at = created_at
where updated_at is null;

update invoices
set vendor = 'A1'
where raw_extracted_text ~* 'AME900814LM3'
  and coalesce(vendor, '') <> 'A1';

update invoices
set vendor = 'T1'
where raw_extracted_text ~* 'TBO140305DH0'
  and coalesce(vendor, '') <> 'T1';

update invoices
set vendor = 'T2'
where raw_extracted_text ~* 'TPT890516JP5'
  and coalesce(vendor, '') <> 'T2';

alter table if exists invoices
    alter column vendor drop not null,
    alter column invoice_number drop not null,
    alter column amount drop not null,
    alter column invoice_date drop not null,
    alter column parse_status set default 'SUCCESS',
    alter column parse_status set not null,
    alter column needs_review set default false,
    alter column needs_review set not null,
    alter column processing_status set default 'SUCCESS',
    alter column processing_status set not null,
    alter column duplicate_flag set default false,
    alter column duplicate_flag set not null,
    alter column updated_at set not null;

create index if not exists idx_invoices_vendor_invoice_number
    on invoices (vendor, invoice_number);

create index if not exists idx_invoices_vendor_amount_invoice_date
    on invoices (vendor, amount, invoice_date);

create table if not exists delivery_records (
    id bigserial primary key,
    item_name varchar(255),
    quantity numeric(12,2),
    delivery_date varchar(32),
    location varchar(255),
    po_number varchar(128),
    entry_note varchar(128),
    raw_text text,
    original_file_name varchar(255) not null,
    created_at timestamp without time zone not null,
    updated_at timestamp without time zone not null
);

create index if not exists idx_delivery_records_created_at
    on delivery_records (created_at desc);

create index if not exists idx_delivery_records_po_number
    on delivery_records (po_number);

create table if not exists order_milestone_import_history (
    id bigserial primary key,
    order_id bigint not null references orders (id) on delete cascade,
    milestone_type varchar(64) not null,
    previous_occurred_at timestamp without time zone,
    new_occurred_at timestamp without time zone not null,
    previous_notes varchar(1024),
    new_notes varchar(1024),
    source_file_name varchar(255) not null,
    imported_at timestamp without time zone not null
);

create index if not exists idx_order_milestone_import_history_imported_at
    on order_milestone_import_history (imported_at desc);

create index if not exists idx_order_milestone_import_history_order_id
    on order_milestone_import_history (order_id);
