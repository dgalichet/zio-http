package zhttp.http

import zio.UIO
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object HExitSpec extends DefaultRunnableSpec with HExitAssertion {
  def spec: ZSpec[Environment, Failure] = {
    import HExit._
    suite("HExit")(
      test("out") {
        empty === isEmpty &&
        succeed(1) === isSuccess(equalTo(1)) &&
        fail(1) === isFailure(equalTo(1)) &&
        effect(UIO(1)) === isEffect
      } +
        test("flatMapError") {
          succeed(0) *> fail(1) <> fail(2) === isFailure(equalTo(2)) &&
          succeed(0) *> fail(1) *> fail(2) === isFailure(equalTo(1))
        } +
        suite("defaultWith")(
          test("succeed") {
            empty <+> succeed(1) === isSuccess(equalTo(1)) &&
            succeed(1) <+> empty === isSuccess(equalTo(1)) &&
            succeed(1) <+> succeed(2) === isSuccess(equalTo(1)) &&
            succeed(1) <+> empty === isSuccess(equalTo(1)) &&
            empty <+> empty === isEmpty
          } +
            test("fail") {
              empty <+> fail(1) === isFailure(equalTo(1)) &&
              fail(1) <+> empty === isFailure(equalTo(1)) &&
              fail(1) <+> fail(2) === isFailure(equalTo(1)) &&
              fail(1) <+> empty === isFailure(equalTo(1))
            } +
            test("empty") {
              empty <+> empty === isEmpty
            } +
            test("effect") {
              effect(UIO(1)) <+> empty === isEffect &&
              empty <+> effect(UIO(1)) === isEffect &&
              empty *> effect(UIO(1)) *> effect(UIO(1)) === isEmpty
            } +
            test("nested succeed") {
              empty <+> succeed(1) <+> succeed(2) === isSuccess(equalTo(1)) &&
              succeed(1) <+> empty <+> succeed(2) === isSuccess(equalTo(1)) &&
              empty <+> empty <+> succeed(1) === isSuccess(equalTo(1))
            } +
            test("flatMap") {
              succeed(0) *> empty <+> succeed(1) === isSuccess(equalTo(1)) &&
              empty *> empty <+> succeed(1) === isSuccess(equalTo(1)) &&
              empty *> empty *> empty <+> succeed(1) === isSuccess(equalTo(1))
            } +
            test("reversed") {
              empty <+> (empty <+> (empty <+> succeed(1))) === isSuccess(equalTo(1))
            },
        ),
    ) @@ timeout(5 second)
  }
}
