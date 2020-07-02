package de.tobiasroeser.mill.integrationtest

import org.scalacheck.{Arbitrary, Gen}

object TestData {

  implicit val arbTestResult: Arbitrary[TestResult] = Arbitrary(
    Gen.oneOf(TestResult.Failed, TestResult.Skipped, TestResult.Success)
  )

  val genTestInvocation = for {
    targets <- Arbitrary.arbitrary[Seq[String]]
    code <- Arbitrary.arbitrary[Int]
  } yield TestInvocation.Targets(targets, code)
  implicit val arbTestInvocation: Arbitrary[TestInvocation] = Arbitrary(genTestInvocation)

  implicit val arbPath: Arbitrary[os.Path] = Arbitrary(
    Gen.oneOf(
      os.root,
      os.root / "tmp",
      os.root / "tmp" / "file.tmp"
    )
  )

  val genTestInvocationResult = for {
    ti <- Arbitrary.arbitrary[TestInvocation]
    r <- Arbitrary.arbitrary[TestResult]
    out <- Arbitrary.arbitrary[Seq[String]]
    err <- Arbitrary.arbitrary[Seq[String]]
    lf <- Arbitrary.arbitrary[Option[os.Path]]
  } yield TestInvocationResult(ti, r, out, err, lf)
  implicit val arbTestInvocationResult: Arbitrary[TestInvocationResult] = Arbitrary(genTestInvocationResult)

}
