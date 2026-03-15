from __future__ import annotations

import unittest

from text_parser import parse_delivery_ocr_text


class DeliveryTextParserTest(unittest.TestCase):
    def test_extracts_fields_from_english_delivery_text(self) -> None:
        result = parse_delivery_ocr_text(
            """
            Mexico City Warehouse
            1.30

            PO: 20250123001
            Entry Note: EN-001

            Item: ONU
            Quantity: 50000
            """
        )

        self.assertEqual(result.location, "Mexico City Warehouse")
        self.assertEqual(result.date, "1.30")
        self.assertEqual(result.po_number, "20250123001")
        self.assertEqual(result.entry_note, "EN-001")
        self.assertEqual(result.item_name, "ONU")
        self.assertEqual(result.quantity, 50000)

    def test_extracts_dynamic_spanish_location(self) -> None:
        result = parse_delivery_ocr_text(
            """
            Guadalajara Bodega
            2025-01-30
            Purchase Order 20250123009
            Delivery Note DN-88
            Producto: Splitter
            Cantidad: 300
            """
        )

        self.assertEqual(result.location, "Guadalajara Bodega")
        self.assertEqual(result.date, "2025-01-30")
        self.assertEqual(result.po_number, "20250123009")
        self.assertEqual(result.entry_note, "DN-88")
        self.assertEqual(result.item_name, "Splitter")
        self.assertEqual(result.quantity, 300)

    def test_extracts_dynamic_chinese_location(self) -> None:
        result = parse_delivery_ocr_text(
            """
            深圳保税仓
            01.30
            PO: 20250123066
            EN: EN-900
            物料: ONU
            数量: 1200
            """
        )

        self.assertEqual(result.location, "深圳保税仓")
        self.assertEqual(result.date, "01.30")
        self.assertEqual(result.po_number, "20250123066")
        self.assertEqual(result.entry_note, "EN-900")
        self.assertEqual(result.item_name, "ONU")
        self.assertEqual(result.quantity, 1200)

    def test_extracts_fields_from_spanish_delivery_table_text(self) -> None:
        result = parse_delivery_ocr_text(
            """
            Ubicación Ciudad de México
            Fecha 3.15
            Orden de compra 20240315009
            Nota de entrada EN-105
            Artículo AAA
            Cantidad 8500
            """
        )

        self.assertEqual(result.location, "Ciudad de México")
        self.assertEqual(result.date, "3.15")
        self.assertEqual(result.po_number, "20240315009")
        self.assertEqual(result.entry_note, "EN-105")
        self.assertEqual(result.item_name, "AAA")
        self.assertEqual(result.quantity, 8500)

    def test_extracts_fields_from_multiline_spanish_delivery_table_text(self) -> None:
        result = parse_delivery_ocr_text(
            """
            Ubicación
            Ciudad de México
            Fecha
            3.15
            Orden de compra
            20240315009
            Nota de entrada
            EN-105
            Artículo
            AAA
            Cantidad
            8500
            """
        )

        self.assertEqual(result.location, "Ciudad de México")
        self.assertEqual(result.date, "3.15")
        self.assertEqual(result.po_number, "20240315009")
        self.assertEqual(result.entry_note, "EN-105")
        self.assertEqual(result.item_name, "AAA")
        self.assertEqual(result.quantity, 8500)

    def test_returns_null_for_missing_fields(self) -> None:
        result = parse_delivery_ocr_text("PO: 20250123001")

        self.assertIsNone(result.item_name)
        self.assertIsNone(result.quantity)
        self.assertIsNone(result.date)
        self.assertIsNone(result.location)
        self.assertEqual(result.po_number, "20250123001")
        self.assertIsNone(result.entry_note)


if __name__ == "__main__":
    unittest.main()
