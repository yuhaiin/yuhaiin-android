#!/usr/bin/env python3
"""Exercise AAR selection when GitHub's workflow-run list returns stale history."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('resolve-latest-core.sh')


def artifact(identifier, run, branch='main', expired=False):
    return {'id': identifier, 'name': 'yuhaiin.aar', 'expired': expired,
            'workflow_run': {'id': run, 'head_branch': branch}}


def workflow(run, conclusion='success', branch='main', path='.github/workflows/go.yml', status='completed'):
    return {'id': run, 'head_sha': f'commit-{run}', 'head_branch': branch,
            'path': path, 'status': status, 'conclusion': conclusion}


class LatestCoreTest(unittest.TestCase):
    def resolve(self, artifacts, runs):
        with tempfile.TemporaryDirectory(prefix='latest-core-test-') as temp:
            root = Path(temp)
            fixture = root / 'fixture.json'
            fixture.write_text(json.dumps({'artifacts': artifacts, 'runs': runs}))
            mock = root / 'gh'
            mock.write_text('''#!/usr/bin/env python3
import json, os, subprocess, sys
from pathlib import Path
args = sys.argv[1:]
fixture = json.loads(Path(os.environ['CORE_TEST_FIXTURE']).read_text())
if args[:2] == ['run', 'list']:
    data = [{'databaseId': 1}]
elif args[0] == 'api' and '/actions/artifacts?' in args[1]:
    data = {'artifacts': fixture['artifacts']}
elif args[0] == 'api' and '/actions/runs/' in args[1]:
    data = fixture['runs'][args[1].rsplit('/', 1)[1]]
else:
    raise SystemExit('Unexpected gh call: ' + repr(args))
query = args[args.index('--jq') + 1]
result = subprocess.run(['jq', '-r', query], input=json.dumps(data), text=True)
raise SystemExit(result.returncode)
''')
            mock.chmod(0o755)
            env = dict(os.environ, PATH=f'{root}:' + os.environ['PATH'],
                       CORE_TEST_FIXTURE=str(fixture))
            return subprocess.run(['bash', str(SCRIPT)], capture_output=True,
                                  text=True, env=env)

    def test_newest_successful_main_aar_despite_stale_run_list(self):
        # Unordered artifacts include an old rerun, a fork branch, expired data,
        # an unfinished build, a failed build, and another workflow's output.
        artifacts = [artifact(100, 1), artifact(20, 2), artifact(200, 2),
                     artifact(30, 3), artifact(40, 4), artifact(50, 5, expired=True),
                     artifact(60, 6, branch='feature'), artifact(70, 7)]
        runs = {str(n): workflow(n) for n in range(1, 8)}
        runs['3'] = workflow(3, conclusion=None, status='in_progress')
        runs['4'] = workflow(4, conclusion='failure')
        runs['7'] = workflow(7, path='.github/workflows/other.yml')
        result = self.resolve(artifacts, runs)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(dict(line.split('=', 1) for line in result.stdout.splitlines()),
                         {'CORE_ARTIFACT_ID': '200', 'CORE_RUN_ID': '2',
                          'CORE_COMMIT': 'commit-2'})

    def test_no_eligible_artifact_stops_instead_of_using_stale_run(self):
        result = self.resolve([artifact(10, 1, expired=True)], {'1': workflow(1)})
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, '')


if __name__ == '__main__':
    unittest.main()
