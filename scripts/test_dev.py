import json, plistlib, subprocess, sys, tempfile, unittest
from pathlib import Path
from unittest import mock
import dev

class CleanupTests(unittest.TestCase):
 def test_persistent_login_job_starts_without_a_desktop_app(self):
  with tempfile.TemporaryDirectory() as temporary:
   agent=Path(temporary)/'dev.plist'
   with mock.patch.object(dev,'AGENT',agent):dev.install_cleanup(persistent=True)
   settings=plistlib.loads(agent.read_bytes())
   self.assertEqual(settings['ProgramArguments'][-2:],['start','--persistent'])
   self.assertTrue(settings['RunAtLoad'])

 def test_persistent_stale_cleanup_preserves_the_dev_world(self):
  with tempfile.TemporaryDirectory() as temporary:
   runtime=Path(temporary)/'runtime';runtime.mkdir();state=runtime/'session.json';control=runtime/'control.sock';agent=Path(temporary)/'dev.plist'
   state.write_text(json.dumps({'persistent':True}));control.write_text('stale socket');agent.write_text('login starter')
   world=runtime/'world.dat';world.write_bytes(b'player testing data')
   with mock.patch.multiple(dev,RUNTIME=runtime,STATE=state,CONTROL=control,AGENT=agent):dev.cleanup_stale()
   self.assertEqual(world.read_bytes(),b'player testing data');self.assertTrue(agent.exists());self.assertFalse(control.exists())

 def test_build_uses_current_matching_plugin_pack_and_checks(self):
  with tempfile.TemporaryDirectory() as temporary:
   root=Path(temporary);(root/'build.gradle.kts').write_text('version = "1.2.0"\n')
   plugin=root/'build/libs/MCCases-1.2.0.jar';plugin.parent.mkdir(parents=True);plugin.write_bytes(b'current plugin')
   pack=root/'build/distributions/MCCases-ResourcePack-Fusion-HD-1.2.0.zip';pack.parent.mkdir(parents=True);pack.write_bytes(b'current pack')
   (plugin.parent/'MCCases-1.1.0.jar').write_bytes(b'old plugin')
   with mock.patch.object(dev,'ROOT',root):
    actual=dev.build_artifacts()
    self.assertEqual(actual,(plugin,root/'build/libs/MCCases-DevChecks-1.2.0.jar',pack))
    pack.unlink()
    with self.assertRaises(FileNotFoundError):dev.build_artifacts()

 def test_app_exit_kills_unresponsive_server_and_finishes_cleanup(self):
  with tempfile.TemporaryDirectory() as temporary:
   runtime=Path(temporary)/'runtime';agent=Path(temporary)/'cleanup.plist'
   agent.write_text('temporary cleanup job')
   server=mock.Mock();server.pid=4242;server.poll.return_value=None
   server.wait.side_effect=[subprocess.TimeoutExpired('paper',25),subprocess.TimeoutExpired('paper',10),0]
   control=mock.Mock()
   with mock.patch.multiple(dev,RUNTIME=runtime,SERVER=runtime/'server',STATE=runtime/'session.json',CONTROL=runtime/'control.sock',AGENT=agent), \
     mock.patch.object(dev,'fingerprint',side_effect=lambda pid:'gone' if pid==1234 else 'owned'), \
     mock.patch.object(dev.subprocess,'Popen',return_value=server), \
     mock.patch.object(dev.socket,'socket',return_value=control), \
     mock.patch.object(dev.os,'chmod'), \
     mock.patch.object(dev.signal,'signal'):
    dev.supervise(1234,'running app')
   server.stdin.write.assert_called_once_with('stop\n')
   server.terminate.assert_called_once_with();server.kill.assert_called_once_with()
   self.assertEqual(server.wait.call_args_list,[mock.call(timeout=25),mock.call(timeout=10),mock.call()])
   control.close.assert_called_once_with()
   self.assertFalse(runtime.exists());self.assertFalse(agent.exists())

 def test_cleanup_stale_owns_only_its_runtime_and_processes(self):
  with tempfile.TemporaryDirectory() as temporary:
   old=dev.RUNTIME,dev.STATE,dev.AGENT
   dev.RUNTIME=Path(temporary)/'runtime';dev.STATE=dev.RUNTIME/'session.json';dev.AGENT=Path(temporary)/'cleanup.plist'
   dev.RUNTIME.mkdir();dev.AGENT.write_text('temporary cleanup job')
   owned=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
   observer=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
   unrelated=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
   try:
    state={'supervisor':owned.pid,'fingerprint':dev.fingerprint(owned.pid),'server':owned.pid,'serverFingerprint':dev.fingerprint(owned.pid),
     'observer':observer.pid,'observerFingerprint':dev.fingerprint(observer.pid),'client':unrelated.pid,'clientFingerprint':'reused PID'}
    dev.STATE.write_text(json.dumps(state));dev.cleanup_stale()
    self.assertTrue(dev.RUNTIME.exists());self.assertIsNone(owned.poll())
    state['fingerprint']='previous boot';dev.STATE.write_text(json.dumps(state));dev.cleanup_stale()
    owned.wait(timeout=5)
    observer.wait(timeout=5)
    self.assertFalse(dev.RUNTIME.exists());self.assertFalse(dev.AGENT.exists());self.assertIsNone(unrelated.poll())
   finally:
    for process in [owned,observer,unrelated]:
     if process.poll() is None:process.terminate()
     process.wait()
    dev.RUNTIME,dev.STATE,dev.AGENT=old

if __name__=='__main__':unittest.main()
