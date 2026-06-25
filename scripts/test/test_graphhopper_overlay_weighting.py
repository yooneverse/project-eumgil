import os
import subprocess
import tempfile
import unittest
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GRAPHOPPER_JAR_URL = "https://repo1.maven.org/maven2/com/graphhopper/graphhopper-web/11.0/graphhopper-web-11.0.jar"
POSTGRES_JAR_URL = "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.5/postgresql-42.7.5.jar"


def cached_jar(url: str) -> Path:
    cache_dir = ROOT / ".ai/LOCAL/graphhopper-test-jars"
    cache_dir.mkdir(parents=True, exist_ok=True)
    target = cache_dir / url.rsplit("/", 1)[-1]
    if not target.exists():
        urllib.request.urlretrieve(url, target)
    return target


class GraphHopperOverlayWeightingTest(unittest.TestCase):
    def test_effective_edge_wrapper_profile_policy_and_reverse_reads(self):
        graphhopper_jar = cached_jar(GRAPHOPPER_JAR_URL)
        postgres_jar = cached_jar(POSTGRES_JAR_URL)
        tmp_dir = ROOT / ".ai/LOCAL/tmp"
        tmp_dir.mkdir(parents=True, exist_ok=True)
        classes_dir = Path(tempfile.mkdtemp(prefix="ieum-gh-overlay-classes-", dir=tmp_dir))
        source_files = [
            *(
                ROOT / "INF/graphhopper/plugin/src/main/java"
            ).glob("com/ssafy/e102/graphhopper/**/*.java"),
            *(
                ROOT / "INF/graphhopper/plugin/src/test/java"
            ).glob("com/ssafy/e102/graphhopper/**/*.java"),
        ]
        classpath = os.pathsep.join([str(graphhopper_jar), str(postgres_jar)])
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes_dir), *map(str, source_files)],
            cwd=ROOT,
            check=True,
        )
        for test_class in [
            "com.ssafy.e102.graphhopper.OverlayAwareWeightingTest",
            "com.ssafy.e102.graphhopper.RoutingOverrideReloadResourceTest",
        ]:
            subprocess.run(
                [
                    "java",
                    "-cp",
                    os.pathsep.join([classpath, str(classes_dir)]),
                    test_class,
                ],
                cwd=ROOT,
                check=True,
            )


if __name__ == "__main__":
    unittest.main()
