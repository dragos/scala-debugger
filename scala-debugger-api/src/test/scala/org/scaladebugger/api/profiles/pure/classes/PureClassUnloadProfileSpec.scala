package org.scaladebugger.api.profiles.pure.classes
import acyclic.file

import com.sun.jdi.event.Event
import org.scaladebugger.api.lowlevel.classes.{ClassUnloadManager, ClassUnloadRequestInfo, PendingClassUnloadSupportLike}
import org.scaladebugger.api.lowlevel.events.EventManager
import org.scaladebugger.api.lowlevel.events.EventType.ClassUnloadEventType
import org.scaladebugger.api.lowlevel.events.data.JDIEventDataResult
import org.scaladebugger.api.lowlevel.events.filters.UniqueIdPropertyFilter
import org.scaladebugger.api.lowlevel.requests.JDIRequestArgument
import org.scaladebugger.api.lowlevel.requests.properties.UniqueIdProperty
import org.scaladebugger.api.pipelines.Pipeline
import org.scaladebugger.api.profiles.Constants
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers, ParallelTestExecution}
import test.JDIMockHelpers

import scala.util.{Failure, Success}

class PureClassUnloadProfileSpec extends FunSpec with Matchers
with ParallelTestExecution with MockFactory with JDIMockHelpers
{
  private val TestRequestId = java.util.UUID.randomUUID().toString
  private val mockClassUnloadManager = mock[ClassUnloadManager]
  private val mockEventManager = mock[EventManager]

  private val pureClassUnloadProfile = new Object with PureClassUnloadProfile {
    private var requestId: String = _
    def setRequestId(requestId: String): Unit = this.requestId = requestId

    // NOTE: If we set a specific request id, return that, otherwise use the
    //       default behavior
    override protected def newClassUnloadRequestId(): String =
      if (requestId != null) requestId else super.newClassUnloadRequestId()

    override protected val classUnloadManager = mockClassUnloadManager
    override protected val eventManager: EventManager = mockEventManager
  }

  describe("PureClassUnloadProfile") {
    describe("#classUnloadRequests") {
      it("should include all active requests") {
        val expected = Seq(
          ClassUnloadRequestInfo(TestRequestId, false)
        )

        val mockClassUnloadManager = mock[PendingClassUnloadSupportLike]
        val pureClassUnloadProfile = new Object with PureClassUnloadProfile {
          override protected val classUnloadManager = mockClassUnloadManager
          override protected val eventManager: EventManager = mockEventManager
        }

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(expected.map(_.requestId)).once()
        (mockClassUnloadManager.getClassUnloadRequestInfo _)
          .expects(TestRequestId).returning(expected.headOption).once()

        (mockClassUnloadManager.pendingClassUnloadRequests _).expects()
          .returning(Nil).once()

        val actual = pureClassUnloadProfile.classUnloadRequests

        actual should be (expected)
      }

      it("should include pending requests if supported") {
        val expected = Seq(
          ClassUnloadRequestInfo(TestRequestId, true)
        )

        val mockClassUnloadManager = mock[PendingClassUnloadSupportLike]
        val pureClassUnloadProfile = new Object with PureClassUnloadProfile {
          override protected val classUnloadManager = mockClassUnloadManager
          override protected val eventManager: EventManager = mockEventManager
        }

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(Nil).once()

        (mockClassUnloadManager.pendingClassUnloadRequests _).expects()
          .returning(expected).once()

        val actual = pureClassUnloadProfile.classUnloadRequests

        actual should be (expected)
      }

      it("should only include active requests if pending unsupported") {
        val expected = Seq(
          ClassUnloadRequestInfo(TestRequestId, false)
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(expected.map(_.requestId)).once()
        (mockClassUnloadManager.getClassUnloadRequestInfo _)
          .expects(TestRequestId).returning(expected.headOption).once()

        val actual = pureClassUnloadProfile.classUnloadRequests

        actual should be (expected)
      }
    }

    describe("#removeClassUnloadRequestWithArgs") {
      it("should return None if no requests exists") {
        val expected = None

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(Nil).once()

        val actual = pureClassUnloadProfile.removeClassUnloadRequestWithArgs()

        actual should be (expected)
      }

      it("should return None if no request with matching extra arguments exists") {
        val expected = None
        val extraArguments = Seq(mock[JDIRequestArgument])

        val requests = Seq(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = true,
            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(requests.map(_.requestId)).once()
        requests.foreach(r =>
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
        )

        val actual = pureClassUnloadProfile.removeClassUnloadRequestWithArgs()

        actual should be (expected)
      }

      it("should return remove and return matching pending requests") {
        val extraArguments = Seq(mock[JDIRequestArgument])

        val expected = Some(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = true,

            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(Seq(expected.get).map(_.requestId)).once()
        expected.foreach(r => {
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
          (mockClassUnloadManager.removeClassUnloadRequest _)
            .expects(r.requestId)
            .returning(true)
            .once()
        })

        val actual = pureClassUnloadProfile.removeClassUnloadRequestWithArgs(
          extraArguments: _*
        )

        actual should be (expected)
      }

      it("should remove and return matching non-pending requests") {
        val extraArguments = Seq(mock[JDIRequestArgument])

        val expected = Some(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = false,

            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(Seq(expected.get).map(_.requestId)).once()
        expected.foreach(r => {
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
          (mockClassUnloadManager.removeClassUnloadRequest _)
            .expects(r.requestId)
            .returning(true)
            .once()
        })

        val actual = pureClassUnloadProfile.removeClassUnloadRequestWithArgs(
          extraArguments: _*
        )

        actual should be (expected)
      }
    }

    describe("#removeAllClassUnloadRequests") {
      it("should return empty if no requests exists") {
        val expected = Nil

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(Nil).once()

        val actual = pureClassUnloadProfile.removeAllClassUnloadRequests()

        actual should be (expected)
      }

      it("should remove and return all pending requests") {
        val extraArguments = Seq(mock[JDIRequestArgument])

        val expected = Seq(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = true,
            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(expected.map(_.requestId)).once()
        expected.foreach(r => {
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
          (mockClassUnloadManager.removeClassUnloadRequest _)
            .expects(r.requestId)
            .returning(true)
            .once()
        })

        val actual = pureClassUnloadProfile.removeAllClassUnloadRequests()

        actual should be (expected)
      }

      it("should remove and return all non-pending requests") {
        val extraArguments = Seq(mock[JDIRequestArgument])

        val expected = Seq(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = false,
            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(expected.map(_.requestId)).once()
        expected.foreach(r => {
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
          (mockClassUnloadManager.removeClassUnloadRequest _)
            .expects(r.requestId)
            .returning(true)
            .once()
        })

        val actual = pureClassUnloadProfile.removeAllClassUnloadRequests()

        actual should be (expected)
      }
    }


    describe("#isClassUnloadRequestWithArgsPending") {
      it("should return false if no requests exist") {
        val expected = false

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(Nil).once()

        val actual = pureClassUnloadProfile.isClassUnloadRequestWithArgsPending()

        actual should be (expected)
      }

      it("should return false if no request with matching extra arguments exists") {
        val expected = false
        val extraArguments = Seq(mock[JDIRequestArgument])

        val requests = Seq(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = true,
            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(requests.map(_.requestId)).once()
        requests.foreach(r =>
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
        )

        val actual = pureClassUnloadProfile.isClassUnloadRequestWithArgsPending()

        actual should be (expected)
      }

      it("should return false if no matching request is pending") {
        val expected = false
        val extraArguments = Seq(mock[JDIRequestArgument])

        val requests = Seq(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = false,
            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(requests.map(_.requestId)).once()
        requests.foreach(r =>
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
        )

        val actual = pureClassUnloadProfile.isClassUnloadRequestWithArgsPending(
          extraArguments: _*
        )

        actual should be (expected)
      }

      it("should return true if at least one matching request is pending") {
        val expected = true
        val extraArguments = Seq(mock[JDIRequestArgument])

        val requests = Seq(
          ClassUnloadRequestInfo(
            requestId = TestRequestId,
            isPending = true,
            extraArguments = extraArguments
          )
        )

        (mockClassUnloadManager.classUnloadRequestList _).expects()
          .returning(requests.map(_.requestId)).once()
        requests.foreach(r =>
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(r.requestId)
            .returning(Some(r))
            .once()
        )

        val actual = pureClassUnloadProfile.isClassUnloadRequestWithArgsPending(
          extraArguments: _*
        )

        actual should be (expected)
      }
    }

    describe("#tryGetOrCreateClassUnloadRequestWithData") {
      it("should create a new request if one has not be made yet") {
        val arguments = Seq(mock[JDIRequestArgument])

        val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
        val uniqueIdPropertyFilter = UniqueIdPropertyFilter(id = TestRequestId)

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureClassUnloadProfile.setRequestId(TestRequestId)

        inSequence {
          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockClassUnloadManager.classUnloadRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockClassUnloadManager.createClassUnloadRequestWithId _)
            .expects(TestRequestId, uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(
          arguments: _*
        )
      }

      it("should capture exceptions thrown when creating the request") {
        val expected = Failure(new Throwable)
        val arguments = Seq(mock[JDIRequestArgument])

        val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureClassUnloadProfile.setRequestId(TestRequestId)

        inSequence {
          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockClassUnloadManager.classUnloadRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockClassUnloadManager.createClassUnloadRequestWithId _)
            .expects(TestRequestId, uniqueIdProperty +: arguments)
            .throwing(expected.failed.get).once()
        }

        val actual = pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(
          arguments: _*
        )

        actual should be (expected)
      }

      it("should create a new request if the previous one was removed") {
        val arguments = Seq(mock[JDIRequestArgument])

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureClassUnloadProfile.setRequestId(TestRequestId)

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureClassUnloadProfile.setRequestId(TestRequestId)

          val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId)

          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockClassUnloadManager.classUnloadRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockClassUnloadManager.createClassUnloadRequestWithId _)
            .expects(TestRequestId, uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(
          arguments: _*
        )

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureClassUnloadProfile.setRequestId(TestRequestId + "other")

          val uniqueIdProperty = UniqueIdProperty(id = TestRequestId + "other")
          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId + "other")

          // Return empty this time to indicate that the vm death request
          // was removed some time between the two calls
          (mockClassUnloadManager.classUnloadRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockClassUnloadManager.createClassUnloadRequestWithId _)
            .expects(TestRequestId + "other", uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId + "other")).once()

          (mockEventManager.addEventDataStream _)
            .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(
          arguments: _*
        )
      }

      it("should not create a new request if the previous one still exists") {
        val arguments = Seq(mock[JDIRequestArgument])

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureClassUnloadProfile.setRequestId(TestRequestId)

          val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId)

          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockClassUnloadManager.classUnloadRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockClassUnloadManager.createClassUnloadRequestWithId _)
            .expects(TestRequestId, uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(
          arguments: _*
        )

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureClassUnloadProfile.setRequestId(TestRequestId + "other")

          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId)

          // Return collection of matching arguments to indicate that we do
          // still have the request
          val internalId = java.util.UUID.randomUUID().toString
          (mockClassUnloadManager.classUnloadRequestList _)
            .expects()
            .returning(Seq(internalId)).once()
          (mockClassUnloadManager.getClassUnloadRequestInfo _)
            .expects(internalId)
            .returning(Some(ClassUnloadRequestInfo(TestRequestId, false, arguments))).once()

          (mockEventManager.addEventDataStream _)
            .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(
          arguments: _*
        )
      }

      it("should remove the underlying request if all pipelines are closed") {
        val arguments = Seq(mock[JDIRequestArgument])

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureClassUnloadProfile.setRequestId(TestRequestId)

        inSequence {
          val eventHandlerIds = Seq("a", "b")
          inAnyOrder {
            val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
            val uniqueIdPropertyFilter =
              UniqueIdPropertyFilter(id = TestRequestId)

            // Memoized request function first checks to make sure the cache
            // has not been invalidated underneath (first call will always be
            // empty since we have never created the request)
            (mockClassUnloadManager.classUnloadRequestList _)
              .expects()
              .returning(Nil).once()
            (mockClassUnloadManager.classUnloadRequestList _)
              .expects()
              .returning(Seq(TestRequestId)).once()

            (mockClassUnloadManager.getClassUnloadRequestInfo _)
              .expects(TestRequestId)
              .returning(Some(ClassUnloadRequestInfo(TestRequestId, false, arguments))).once()

            // NOTE: Expect the request to be created with a unique id
            (mockClassUnloadManager.createClassUnloadRequestWithId _)
              .expects(TestRequestId, uniqueIdProperty +: arguments)
              .returning(Success(TestRequestId)).once()

            // NOTE: Pipeline adds an event handler id to its metadata
            def newEventPipeline(id: String) = Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            ).withMetadata(Map(EventManager.EventHandlerIdMetadataField -> id))

            eventHandlerIds.foreach(id => {
              (mockEventManager.addEventDataStream _)
                .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
                .returning(newEventPipeline(id)).once()
            })
          }

          (mockClassUnloadManager.removeClassUnloadRequest _)
            .expects(TestRequestId).once()
          eventHandlerIds.foreach(id => {
            (mockEventManager.removeEventHandler _).expects(id).once()
          })
        }

        val p1 = pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(arguments: _*)
        val p2 = pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(arguments: _*)

        p1.foreach(_.close())
        p2.foreach(_.close())
      }

      it("should remove the underlying request if close data says to do so") {
        val arguments = Seq(mock[JDIRequestArgument])

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureClassUnloadProfile.setRequestId(TestRequestId)

        inSequence {
          val eventHandlerIds = Seq("a", "b")
          inAnyOrder {
            val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
            val uniqueIdPropertyFilter =
              UniqueIdPropertyFilter(id = TestRequestId)

            // Memoized request function first checks to make sure the cache
            // has not been invalidated underneath (first call will always be
            // empty since we have never created the request)
            (mockClassUnloadManager.classUnloadRequestList _)
              .expects()
              .returning(Nil).once()
            (mockClassUnloadManager.classUnloadRequestList _)
              .expects()
              .returning(Seq(TestRequestId)).once()

            (mockClassUnloadManager.getClassUnloadRequestInfo _)
              .expects(TestRequestId)
              .returning(Some(ClassUnloadRequestInfo(TestRequestId, false, arguments))).once()

            // NOTE: Expect the request to be created with a unique id
            (mockClassUnloadManager.createClassUnloadRequestWithId _)
              .expects(TestRequestId, uniqueIdProperty +: arguments)
              .returning(Success(TestRequestId)).once()

            // NOTE: Pipeline adds an event handler id to its metadata
            def newEventPipeline(id: String) = Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            ).withMetadata(Map(EventManager.EventHandlerIdMetadataField -> id))

            eventHandlerIds.foreach(id => {
              (mockEventManager.addEventDataStream _)
                .expects(ClassUnloadEventType, Seq(uniqueIdPropertyFilter))
                .returning(newEventPipeline(id)).once()
            })
          }

          (mockClassUnloadManager.removeClassUnloadRequest _)
            .expects(TestRequestId).once()
          eventHandlerIds.foreach(id => {
            (mockEventManager.removeEventHandler _).expects(id).once()
          })
        }

        val p1 = pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(arguments: _*)
        val p2 = pureClassUnloadProfile.tryGetOrCreateClassUnloadRequestWithData(arguments: _*)

        p1.foreach(_.close(now = true, data = Constants.CloseRemoveAll))
      }
    }
  }
}

