package org.scaladebugger.api.profiles.pure.requests.steps

import org.scaladebugger.api.profiles.traits.info.ThreadInfoProfile
import org.scaladebugger.api.virtualmachines.DummyScalaVirtualMachine
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Seconds
import org.scalatest.{FunSpec, Matchers, ParallelTestExecution}
import test.{TestUtilities, VirtualMachineFixtures}

class PureStepProfileScala211IntegrationSpec extends FunSpec with Matchers
  with ParallelTestExecution with VirtualMachineFixtures
  with TestUtilities with Eventually
{
  describe("PureStepProfile for 2.11") {
    describe("stepping over") {
      it("should skip over each iteration") {
        val testClass = "org.scaladebugger.test.steps.BasicIterations"

        // Start on first line of main method
        val startingLine = 13

        // Running through multiple scenarios in this test
        // NOTE: These expectations were made based off of IntelliJ's handling
        val expectedReachableLines = Seq(
          // Can skip entirely over for comprehension
          16,
          // Enters the foreach once (as part of anonymous function declaration?)
          21, 22, 21,
          // Enters the map once (as part of anonymous function declaration?)
          26, 27, 26,
          // Can skip entirely over reduce (why?)
          31,
          // Function object creation
          36,
          // Execution function
          45,
          // Execute method
          47,
          // No-op
          49
        )

        val s = DummyScalaVirtualMachine.newInstance()
        val verify = verifyStepsOnEach(
          testClass = testClass,
          scalaVirtualMachine = s,
          startingLine = startingLine,
          expectedReachableLines = expectedReachableLines,
          failIfNotExact = true,
          maxDuration = (15, Seconds)
        )

        // NOTE: Have to up the maximum duration due to the delay caused by
        //       the for comprehension
        withLazyVirtualMachine(testClass, pendingScalaVirtualMachines = Seq(s)) { (s, f) =>
          verify(s, f, s.stepOverLine(_: ThreadInfoProfile))
        }
      }
    }
  }
}