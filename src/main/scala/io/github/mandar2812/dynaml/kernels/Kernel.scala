package io.github.mandar2812.dynaml.kernels

import breeze.linalg.DenseMatrix


/**
 * Defines a base class for kernels
 * defined on arbitrary objects.
 *
 * @tparam T The domain over which kernel function
 *           k(x, y) is defined. i.e. x,y belong to T
 * @tparam V The type of value returned by the kernel function
 *           k(x,y)
 * */
trait Kernel[T, V] {
  def evaluate(x: T, y: T): V
}

/**
  * A covariance function implementation. Covariance functions are
  * central to Stochastic Process Models as well as SVMs.
  * */
abstract class CovarianceFunction[T, V, M] extends Kernel[T, V] {

  val hyper_parameters: List[String]

  var state: Map[String, Double] = Map()

  def setHyperParameters(h: Map[String, Double]): this.type = {
    assert(hyper_parameters.forall(h contains _),
      "All hyper parameters must be contained in the arguments")
    hyper_parameters.foreach((key) => {
      state += (key -> h(key))
    })
    this
  }

  def gradient(x: T, y: T): Map[String, V]

  def buildKernelMatrix[S <: Seq[T]](mappedData: S,
                                     length: Int): KernelMatrix[M]

  def buildCrossKernelMatrix[S <: Seq[T]](dataset1: S, dataset2: S): M
}

abstract class CompositeCovariance[T]
  extends LocalScalarKernel[T] {

}

/**
  * Scalar Kernel defines algebraic behavior for kernels of the form
  * K: Index x Index -> Double, i.e. kernel functions whose output
  * is a scalar/double value. Generic behavior for these kernels
  * is given by the ability to add and multiply valid kernels to
  * create new valid scalar kernel functions.
  *
  * */
trait LocalScalarKernel[Index] extends
CovarianceFunction[Index, Double, DenseMatrix[Double]] {

  def gradient(x: Index, y: Index): Map[String, Double] = hyper_parameters.map((_, 0.0)).toMap

  def +[T <: LocalScalarKernel[Index]](otherKernel: T): CompositeCovariance[Index] = {

    val firstKern = this

    new CompositeCovariance[Index] {
      override val hyper_parameters = firstKern.hyper_parameters ++ otherKernel.hyper_parameters

      override def evaluate(x: Index, y: Index) = firstKern.evaluate(x,y) + otherKernel.evaluate(x,y)

      state = firstKern.state ++ otherKernel.state

      override def gradient(x: Index, y: Index): Map[String, Double] =
        firstKern.gradient(x, y) ++ otherKernel.gradient(x,y)

      override def buildKernelMatrix[S <: Seq[Index]](mappedData: S, length: Int) =
        SVMKernel.buildSVMKernelMatrix[S, Index](mappedData, length, this.evaluate)

      override def buildCrossKernelMatrix[S <: Seq[Index]](dataset1: S, dataset2: S) =
        SVMKernel.crossKernelMatrix(dataset1, dataset2, this.evaluate)

    }
  }

  def *[T <: LocalScalarKernel[Index]](otherKernel: T): CompositeCovariance[Index] = {

    val firstKern = this

    new CompositeCovariance[Index] {
      override val hyper_parameters = firstKern.hyper_parameters ++ otherKernel.hyper_parameters

      override def evaluate(x: Index, y: Index) = firstKern.evaluate(x,y) * otherKernel.evaluate(x,y)

      state = firstKern.state ++ otherKernel.state

      override def gradient(x: Index, y: Index): Map[String, Double] =
        firstKern.gradient(x, y).map((couple) => (couple._1, couple._2*otherKernel.evaluate(x,y))) ++
          otherKernel.gradient(x,y).map((couple) => (couple._1, couple._2*firstKern.evaluate(x,y)))

      override def buildKernelMatrix[S <: Seq[Index]](mappedData: S, length: Int) =
        SVMKernel.buildSVMKernelMatrix[S, Index](mappedData, length, this.evaluate)

      override def buildCrossKernelMatrix[S <: Seq[Index]](dataset1: S, dataset2: S) =
        SVMKernel.crossKernelMatrix(dataset1, dataset2, this.evaluate)

    }
  }

}


