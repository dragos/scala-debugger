package org.scaladebugger.api.profiles.java.requests.threads

import java.util.concurrent.atomic.AtomicInteger

import org.scaladebugger.api.profiles.java.JavaDebugProfile
import org.scaladebugger.api.utils.JDITools
import org.scaladebugger.api.virtualmachines.DummyScalaVirtualMachine
import org.scaladebugger.test.helpers.ParallelMockFunSpec
import test.{ApiTestUtilities, VirtualMachineFixtures}

class JavaThreadStartRequestIntegrationSpec extends ParallelMockFunSpec
  with VirtualMachineFixtures
  with ApiTestUtilities
{
  describe("JavaThreadStartRequest") {
    it("should trigger when a thread starts") {
      val testClass = "org.scaladebugger.test.threads.ThreadStart"
      val testFile = JDITools.scalaClassStringToFileString(testClass)

      val threadStartCount = new AtomicInteger(0)

      val s = DummyScalaVirtualMachine.newInstance()

      // Mark that we want to receive thread start events
      s.withProfile(JavaDebugProfile.Name)
        .getOrCreateThreadStartRequest()
        .map(_.thread.name)
        .filter(_.startsWith("test thread"))
        .foreach(_ => threadStartCount.incrementAndGet())

      // Start our Thread and listen for the start event
      withVirtualMachine(testClass, pendingScalaVirtualMachines = Seq(s)) { (s) =>
        // Eventually, we should receive a total of 10 thread starts
        logTimeTaken(eventually {
          threadStartCount.get() should be (10)
        })
      }
    }
  }
}
