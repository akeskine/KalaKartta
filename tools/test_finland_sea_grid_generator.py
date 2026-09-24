import unittest

from shapely.geometry import LineString, MultiPolygon, box

from tools import finland_sea_grid_generator as generator


class FinlandSeaGridGeneratorTest(unittest.TestCase):
    def test_positive_area_intersections_mark_partial_cells(self):
        _, _, columns, rows, payload = generator.rasterise(
            (box(50, 50, 150, 150), [LineString([(50, 50), (150, 50)])])
        )

        self.assertEqual((columns, rows), (2, 2))
        self.assertEqual(payload, bytearray([0xF0]))

    def test_edge_only_contacts_do_not_mark_cells(self):
        sea = MultiPolygon([box(0, 0, 100, 100), box(200, 0, 300, 100)])
        _, _, columns, rows, payload = generator.rasterise(
            (sea, [LineString([(0, 0), (300, 0)])])
        )

        self.assertEqual((columns, rows), (3, 1))
        self.assertEqual(payload, bytearray([0xA0]))

    def test_exact_100_km_tangent_has_no_positive_area(self):
        sea = box(0, 0, 100, 100)
        shoreline = LineString([(0, -100_000), (100, -100_000)])
        _, _, _, _, payload = generator.rasterise((sea, [shoreline]))

        self.assertEqual(payload, bytearray([0x00]))

    def test_cell_inside_100_km_buffer_is_marked(self):
        sea = box(0, 0, 100, 100)
        shoreline = LineString([(0, -99_999.9), (100, -99_999.9)])
        _, _, _, _, payload = generator.rasterise((sea, [shoreline]))

        self.assertEqual(payload, bytearray([0x80]))


if __name__ == "__main__":
    unittest.main()