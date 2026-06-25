import csv
import tempfile
import unittest
from pathlib import Path

import sys

ROOT_DIR = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT_DIR / "scripts" / "db"))

import load_subway_timetables as loader  # noqa: E402


class SubwayTimetableLoaderTest(unittest.TestCase):
    def test_flattens_odsay_schedule_payload_to_station_and_timetable_rows(self):
        payload = {
            "result": {
                "stationID": 130,
                "stationName": "서면",
                "laneName": "부산 1호선",
                "weekdaySchedule": {
                    "up": [
                        {
                            "departureTime": "05:32",
                            "endStationName": "노포",
                        }
                    ],
                    "down": [
                        {
                            "departureTime": "25:05",
                            "endStationName": "다대포해수욕장",
                        }
                    ],
                },
                "saturdaySchedule": {
                    "up": [
                        {
                            "departureTime": "06:10",
                            "endStationName": "노포",
                        }
                    ]
                },
                "holidaySchedule": {},
            }
        }

        stations, timetables = loader.rows_from_schedule_payload(payload)

        self.assertEqual(
            stations,
            [
                loader.SubwayStationCsvRow(
                    odsay_station_id="130",
                    station_name="서면",
                    line_name="부산 1호선",
                    point="",
                )
            ],
        )
        self.assertEqual(len(timetables), 3)
        self.assertEqual(timetables[0].service_day_type, "WEEKDAY")
        self.assertEqual(timetables[0].way_code, "1")
        self.assertEqual(timetables[0].departure_second_of_day, "19920")
        self.assertEqual(timetables[1].departure_second_of_day, "90300")
        self.assertEqual(timetables[2].service_day_type, "SATURDAY")

    def test_writes_csv_and_validates_expected_rows(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            csv_dir = Path(temp_dir)
            loader.write_csvs(
                [
                    loader.SubwayStationCsvRow(
                        odsay_station_id="130",
                        station_name="서면",
                        line_name="부산 1호선",
                        point="SRID=4326;POINT(129.059170 35.157918)",
                    )
                ],
                [
                    loader.SubwayTimetableCsvRow(
                        odsay_station_id="130",
                        service_day_type="WEEKDAY",
                        way_code="1",
                        departure_time_text="05:32",
                        departure_second_of_day="19920",
                        end_station_name="노포",
                    )
                ],
                csv_dir,
            )

            issues = loader.validate_csv_dir(csv_dir)

        self.assertFalse(issues)

    def test_validation_blocks_copy_when_required_values_are_invalid(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            csv_dir = Path(temp_dir)
            self.write_csv(
                csv_dir / loader.STATION_CSV_NAME,
                loader.STATION_CSV_HEADER,
                [
                    ["130", "서면", "부산 1호선", ""],
                    ["130", "서면", "부산 1호선", ""],
                ],
            )
            self.write_csv(
                csv_dir / loader.TIMETABLE_CSV_NAME,
                loader.TIMETABLE_CSV_HEADER,
                [["999", "SUNDAY", "3", "", "abc", ""]],
            )

            issues = loader.validate_csv_dir(csv_dir)

        self.assertIn("duplicate subway_stations.odsay_station_id: 130", issues)
        self.assertIn("row 2: odsay_station_id not found in subway_stations: 999", issues)
        self.assertIn("row 2: invalid service_day_type: SUNDAY", issues)
        self.assertIn("row 2: invalid way_code: 3", issues)
        self.assertIn("row 2: departure_time_text is required", issues)
        self.assertIn("row 2: departure_second_of_day must be an integer", issues)
        self.assertIn("row 2: end_station_name is required", issues)

    def test_validation_blocks_duplicate_timetable_lookup_key(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            csv_dir = Path(temp_dir)
            self.write_csv(
                csv_dir / loader.STATION_CSV_NAME,
                loader.STATION_CSV_HEADER,
                [["130", "서면", "부산 1호선", ""]],
            )
            self.write_csv(
                csv_dir / loader.TIMETABLE_CSV_NAME,
                loader.TIMETABLE_CSV_HEADER,
                [
                    ["130", "WEEKDAY", "1", "05:32", "19920", "노포"],
                    ["130", "WEEKDAY", "1", "05:32", "19920", "노포"],
                ],
            )

            issues = loader.validate_csv_dir(csv_dir)

        self.assertIn(
            "duplicate subway_timetables lookup key: "
            "130|WEEKDAY|1|19920|노포",
            issues,
        )

    def test_validation_blocks_empty_csv_artifacts(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            csv_dir = Path(temp_dir)
            self.write_csv(csv_dir / loader.STATION_CSV_NAME, loader.STATION_CSV_HEADER, [])
            self.write_csv(csv_dir / loader.TIMETABLE_CSV_NAME, loader.TIMETABLE_CSV_HEADER, [])

            issues = loader.validate_csv_dir(csv_dir)

        self.assertIn("subway_stations.csv has no rows", issues)
        self.assertIn("subway_timetables.csv has no rows", issues)

    @staticmethod
    def write_csv(path, headers, rows):
        with path.open("w", newline="", encoding="utf-8") as file:
            writer = csv.writer(file)
            writer.writerow(headers)
            writer.writerows(rows)


if __name__ == "__main__":
    unittest.main()
