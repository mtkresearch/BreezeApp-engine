Using AAR from Maven Central

ExecuTorch is available on Maven Central.

Simply add the target org.pytorch:executorch-android:0.6.0-rc1 to your Android app dependency (build.gradle), and build your app.

For example:

# app/build.gradle.kts
dependencies {
    implementation("org.pytorch:executorch-android:0.6.0-rc1")
}


Package	Description
org.pytorch.executorch	
org.pytorch.executorch.annotations	
org.pytorch.executorch.extension.llm

Classes:
DType
EValue
Module
Tensor
Experimental
LlmCallback
LlmModule

Class LlmModule


public class LlmModule

                    
LlmModule is a wrapper around the Executorch LLM. It provides a simple interface to generate text from the model.

Warning: These APIs are experimental and subject to change without notice

Field Summary

Fields
Modifier and Type	Field	Description
public final static int	MODEL_TYPE_TEXT	
public final static int	MODEL_TYPE_TEXT_VISION	
Constructor Summary

Constructors
Constructor	Description
LlmModule(String modulePath, String tokenizerPath, float temperature)	Constructs a LLM Module for a model with given model path, tokenizer, temperature.
LlmModule(String modulePath, String tokenizerPath, float temperature, String dataPath)	Constructs a LLM Module for a model with given model path, tokenizer, temperature and datapath.
LlmModule(int modelType, String modulePath, String tokenizerPath, float temperature)	Constructs a LLM Module for a model with given path, tokenizer, and temperature.
Method Summary

All Methods
Modifier and Type	Method	Description
void	resetNative()	
int	generate(String prompt, LlmCallback llmCallback)	Start generating tokens from the module.
int	generate(String prompt, int seqLen, LlmCallback llmCallback)	Start generating tokens from the module.
int	generate(String prompt, LlmCallback llmCallback, boolean echo)	Start generating tokens from the module.
int	generate(String prompt, int seqLen, LlmCallback llmCallback, boolean echo)	Start generating tokens from the module.
native int	generate(Array<int> image, int width, int height, int channels, String prompt, int seqLen, LlmCallback llmCallback, boolean echo)	Start generating tokens from the module.
long	prefillImages(Array<int> image, int width, int height, int channels, long startPos)	Prefill an LLaVA Module with the given images input.
long	prefillPrompt(String prompt, long startPos, int bos, int eos)	Prefill an LLaVA Module with the given text input.
native int	generateFromPos(String prompt, int seqLen, long startPos, LlmCallback callback, boolean echo)	Generate tokens from the given prompt, starting from the given position.
native void	stop()	Stop current generate() before it finishes.
native int	load()	Force loading the module.
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait
Constructor Detail

LlmModule
LlmModule(String modulePath, String tokenizerPath, float temperature)
Constructs a LLM Module for a model with given model path, tokenizer, temperature.
LlmModule
LlmModule(String modulePath, String tokenizerPath, float temperature, String dataPath)
Constructs a LLM Module for a model with given model path, tokenizer, temperature and datapath.
LlmModule
LlmModule(int modelType, String modulePath, String tokenizerPath, float temperature)
Constructs a LLM Module for a model with given path, tokenizer, and temperature.
Method Detail

resetNative
void resetNative()
generate
int generate(String prompt, LlmCallback llmCallback)
Start generating tokens from the module.

Parameters:
prompt - Input prompt
llmCallback - callback object to receive results.
generate
int generate(String prompt, int seqLen, LlmCallback llmCallback)
Start generating tokens from the module.

Parameters:
prompt - Input prompt
seqLen - sequence length
llmCallback - callback object to receive results.
generate
int generate(String prompt, LlmCallback llmCallback, boolean echo)
Start generating tokens from the module.

Parameters:
prompt - Input prompt
llmCallback - callback object to receive results
echo - indicate whether to echo the input prompt or not (text completion vs chat)
generate
int generate(String prompt, int seqLen, LlmCallback llmCallback, boolean echo)
Start generating tokens from the module.

Parameters:
prompt - Input prompt
seqLen - sequence length
llmCallback - callback object to receive results
echo - indicate whether to echo the input prompt or not (text completion vs chat)
generate
native int generate(Array<int> image, int width, int height, int channels, String prompt, int seqLen, LlmCallback llmCallback, boolean echo)
Start generating tokens from the module.

Parameters:
image - Input image as a byte array
width - Input image width
height - Input image height
channels - Input image number of channels
prompt - Input prompt
seqLen - sequence length
llmCallback - callback object to receive results.
echo - indicate whether to echo the input prompt or not (text completion vs chat)
prefillImages
long prefillImages(Array<int> image, int width, int height, int channels, long startPos)
Prefill an LLaVA Module with the given images input.

Parameters:
image - Input image as a byte array
width - Input image width
height - Input image height
channels - Input image number of channels
startPos - The starting position in KV cache of the input in the LLM.
prefillPrompt
long prefillPrompt(String prompt, long startPos, int bos, int eos)
Prefill an LLaVA Module with the given text input.

Parameters:
prompt - The text prompt to LLaVA.
startPos - The starting position in KV cache of the input in the LLM.
bos - The number of BOS (begin of sequence) token.
eos - The number of EOS (end of sequence) token.
generateFromPos
native int generateFromPos(String prompt, int seqLen, long startPos, LlmCallback callback, boolean echo)
Generate tokens from the given prompt, starting from the given position.

Parameters:
prompt - The text prompt to LLaVA.
seqLen - The total sequence length, including the prompt tokens and new tokens.
startPos - The starting position in KV cache of the input in the LLM.
callback - callback object to receive results.
echo - indicate whether to echo the input prompt or not.
stop
native void stop()
Stop current generate() before it finishes.

load
native int load()
Force loading the module. Otherwise the model is loaded during first generate().


Package 
Interface LlmCallback


public interface LlmCallback

                    
Callback interface for Llama model. Users can implement this interface to receive the generated tokens and statistics.

Warning: These APIs are experimental and subject to change without notice

Method Summary

All Methods
Modifier and Type	Method	Description
abstract void	onResult(String result)	Called when a new result is available from JNI.
abstract void	onStats(float tps)	Called when the statistics for the generate() is available.
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait
Method Detail

onResult
abstract void onResult(String result)
Called when a new result is available from JNI. Users will keep getting onResult() invocationsuntil generate() finishes.

Parameters:
result - Last generated token
onStats
abstract void onStats(float tps)
Called when the statistics for the generate() is available.

Parameters:
tps - Tokens/second for generated tokens.

Package 
Enum DType

All Implemented Interfaces:
java.io.Serializable , java.lang.Comparable

public enum DType

                    
Codes representing tensor data types.

Warning: These APIs are experimental and subject to change without notice

Enum Constant Summary

Enum Constants
Enum Constant	Description
BITS16	
Code for dtype ScalarType::Bits16

BITS8	
Code for dtype ScalarType::Bits8

BITS4X2	
Code for dtype ScalarType::Bits4x2

BITS2X4	
Code for dtype ScalarType::Bits2x4

BITS1X8	
Code for dtype ScalarType::Bits1x8

QINT2X4	
Code for dtype ScalarType::QUInt2x4

QINT4X2	
Code for dtype ScalarType::QUInt4x2

BFLOAT16	
Code for dtype ScalarType::BFloat16

QINT32	
Code for dtype ScalarType::QInt32

QUINT8	
Code for dtype ScalarType::QUInt8

QINT8	
Code for dtype ScalarType::QInt8

BOOL	
Code for dtype ScalarType::Bool

COMPLEX_DOUBLE	
Code for dtype ScalarType::ComplexDouble

COMPLEX_FLOAT	
Code for dtype ScalarType::ComplexFloat

COMPLEX_HALF	
Code for dtype ScalarType::ComplexHalf

DOUBLE	
Code for dtype ScalarType::Double

FLOAT	
Code for dtype ScalarType::Float

HALF	
Code for dtype ScalarType::Half

INT64	
Code for dtype ScalarType::Long

INT32	
Code for dtype ScalarType::Int

INT16	
Code for dtype ScalarType::Short

INT8	
Code for dtype ScalarType::Char

UINT8	
Code for dtype ScalarType::Byte

Method Summary

All Methods
Modifier and Type	Method	Description
static Array<DType>	values()	
static DType	valueOf(String name)	
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait
Method Detail

values
static Array<DType> values()
valueOf
static DType valueOf(String name)

Package 
Class EValue


public class EValue

                    
Java representation of an ExecuTorch value, which is implemented as tagged union that can be one of the supported types: https://pytorch.org/docs/stable/jit.html#types .

Calling {@code toX} methods for inappropriate types will throw IllegalStateException.

{@code EValue} objects are constructed with {@code EValue.from(value)}, {@code * EValue.tupleFrom(value1, value2, ...)}, {@code EValue.listFrom(value1, value2, ...)}, or one of the {@code dict} methods, depending on the key type.

Data is retrieved from {@code EValue} objects with the {@code toX()} methods. Note that {@code * str}-type EValues must be extracted with toStr, rather than toString.

{@code EValue} objects may retain references to objects passed into their constructors, and may return references to their internal state from {@code toX()}.

Warning: These APIs are experimental and subject to change without notice

Method Summary

All Methods
Modifier and Type	Method	Description
boolean	isNone()	
boolean	isTensor()	
boolean	isBool()	
boolean	isInt()	
boolean	isDouble()	
boolean	isString()	
static EValue	optionalNone()	Creates a new {@code EValue} of type {@code Optional} that contains no value.
static EValue	from(Tensor tensor)	Creates a new {@code EValue} of type {@code Tensor}.
static EValue	from(boolean value)	Creates a new {@code EValue} of type {@code bool}.
static EValue	from(long value)	Creates a new {@code EValue} of type {@code int}.
static EValue	from(double value)	Creates a new {@code EValue} of type {@code double}.
static EValue	from(String value)	Creates a new {@code EValue} of type {@code str}.
Tensor	toTensor()	
boolean	toBool()	
long	toInt()	
double	toDouble()	
String	toStr()	
Array<byte>	toByteArray()	Serializes an {@code EValue} into a byte array.
static EValue	fromByteArray(Array<byte> bytes)	Deserializes an {@code EValue} from a byte[].
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait
Method Detail

isNone
boolean isNone()
isTensor
boolean isTensor()
isBool
boolean isBool()
isInt
boolean isInt()
isDouble
boolean isDouble()
isString
boolean isString()
optionalNone
static EValue optionalNone()
Creates a new {@code EValue} of type {@code Optional} that contains no value.

from
static EValue from(Tensor tensor)
Creates a new {@code EValue} of type {@code Tensor}.

from
static EValue from(boolean value)
Creates a new {@code EValue} of type {@code bool}.

from
static EValue from(long value)
Creates a new {@code EValue} of type {@code int}.

from
static EValue from(double value)
Creates a new {@code EValue} of type {@code double}.

from
static EValue from(String value)
Creates a new {@code EValue} of type {@code str}.

toTensor
Tensor toTensor()
toBool
boolean toBool()
toInt
long toInt()
toDouble
double toDouble()
toStr
String toStr()
toByteArray
Array<byte> toByteArray()
Serializes an {@code EValue} into a byte array. Note: This method is experimental and subjectto change without notice.

fromByteArray
static EValue fromByteArray(Array<byte> bytes)
Deserializes an {@code EValue} from a byte[]. Note: This method is experimental and subject tochange without notice.

Parameters:
bytes - The byte array to deserialize from.


Package 
Class Module


public class Module

                    
Java wrapper for ExecuTorch Module.

Warning: These APIs are experimental and subject to change without notice

Field Summary

Fields
Modifier and Type	Field	Description
public final static int	LOAD_MODE_FILE	
public final static int	LOAD_MODE_MMAP	
public final static int	LOAD_MODE_MMAP_USE_MLOCK	
public final static int	LOAD_MODE_MMAP_USE_MLOCK_IGNORE_ERRORS	
Method Summary

All Methods
Modifier and Type	Method	Description
static Module	load(String modelPath, int loadMode)	Loads a serialized ExecuTorch module from the specified path on the disk.
static Module	load(String modelPath)	Loads a serialized ExecuTorch module from the specified path on the disk to run on CPU.
Array<EValue>	forward(Array<EValue> inputs)	Runs the 'forward' method of this module with the specified arguments.
Array<EValue>	execute(String methodName, Array<EValue> inputs)	Runs the specified method of this module with the specified arguments.
int	loadMethod(String methodName)	Load a method on this module.
Array<String>	readLogBuffer()	Retrieve the in-memory log buffer, containing the most recent ExecuTorch log entries.
void	destroy()	Explicitly destroys the native torch::jit::Module.
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait
Method Detail

load
static Module load(String modelPath, int loadMode)
Loads a serialized ExecuTorch module from the specified path on the disk.

Parameters:
modelPath - path to file that contains the serialized ExecuTorch module.
loadMode - load mode for the module.
load
static Module load(String modelPath)
Loads a serialized ExecuTorch module from the specified path on the disk to run on CPU.

Parameters:
modelPath - path to file that contains the serialized ExecuTorch module.
forward
Array<EValue> forward(Array<EValue> inputs)
Runs the 'forward' method of this module with the specified arguments.

Parameters:
inputs - arguments for the ExecuTorch module's 'forward' method.
execute
Array<EValue> execute(String methodName, Array<EValue> inputs)
Runs the specified method of this module with the specified arguments.

Parameters:
methodName - name of the ExecuTorch method to run.
inputs - arguments that will be passed to ExecuTorch method.
loadMethod
int loadMethod(String methodName)
Load a method on this module. This might help with the first time inference performance,because otherwise the method is loaded lazily when it's execute. Note: this function issynchronous, and will block until the method is loaded. Therefore, it is recommended to callthis on a background thread. However, users need to make sure that they don't execute beforethis function returns.

readLogBuffer
Array<String> readLogBuffer()
Retrieve the in-memory log buffer, containing the most recent ExecuTorch log entries.

destroy
void destroy()
Explicitly destroys the native torch::jit::Module. Calling this method is not required, as thenative object will be destroyed when this object is garbage-collected. However, the timing ofgarbage collection is not guaranteed, so proactively calling {@code destroy} can free memorymore quickly. See resetNative.


Package 
Class Tensor


public abstract class Tensor

                    
Representation of an ExecuTorch Tensor. Behavior is similar to PyTorch's tensor objects.

Most tensors will be constructed as {@code Tensor.fromBlob(data, shape)}, where {@code data} can be an array or a direct Buffer (of the proper subclass). Helper methods are provided to allocate buffers properly.

To access Tensor data, see dtype, shape, and various {@code getDataAs*} methods.

When constructing {@code Tensor} objects with {@code data} as an array, it is not specified whether this data is copied or retained as a reference so it is recommended not to modify it after constructing. {@code data} passed as a Buffer is not copied, so it can be modified between Module calls to avoid reallocation. Data retrieved from {@code Tensor} objects may be copied or may be a reference to the {@code Tensor}'s internal data buffer. {@code shape} is always copied.

Warning: These APIs are experimental and subject to change without notice

Method Summary

All Methods
Modifier and Type	Method	Description
static ByteBuffer	allocateByteBuffer(int numElements)	Allocates a new direct ByteBuffer with native byte order with specified capacity thatcan be used in fromBlob, .
static IntBuffer	allocateIntBuffer(int numElements)	Allocates a new direct IntBuffer with native byte order with specified capacity thatcan be used in fromBlob.
static FloatBuffer	allocateFloatBuffer(int numElements)	Allocates a new direct FloatBuffer with native byte order with specified capacity thatcan be used in fromBlob.
static LongBuffer	allocateLongBuffer(int numElements)	Allocates a new direct LongBuffer with native byte order with specified capacity thatcan be used in fromBlob.
static DoubleBuffer	allocateDoubleBuffer(int numElements)	Allocates a new direct DoubleBuffer with native byte order with specified capacity thatcan be used in fromBlob.
static Tensor	fromBlobUnsigned(Array<byte> data, Array<long> shape)	Creates a new Tensor instance with dtype torch.uint8 with specified shape and data as array ofbytes.
static Tensor	fromBlob(Array<byte> data, Array<long> shape)	Creates a new Tensor instance with dtype torch.int8 with specified shape and data as array ofbytes.
static Tensor	fromBlob(Array<int> data, Array<long> shape)	Creates a new Tensor instance with dtype torch.int32 with specified shape and data as array ofints.
static Tensor	fromBlob(Array<float> data, Array<long> shape)	Creates a new Tensor instance with dtype torch.float32 with specified shape and data as arrayof floats.
static Tensor	fromBlob(Array<long> data, Array<long> shape)	Creates a new Tensor instance with dtype torch.int64 with specified shape and data as array oflongs.
static Tensor	fromBlob(Array<double> data, Array<long> shape)	Creates a new Tensor instance with dtype torch.float64 with specified shape and data as arrayof doubles.
static Tensor	fromBlobUnsigned(ByteBuffer data, Array<long> shape)	Creates a new Tensor instance with dtype torch.uint8 with specified shape and data.
static Tensor	fromBlob(ByteBuffer data, Array<long> shape)	Creates a new Tensor instance with dtype torch.int8 with specified shape and data.
static Tensor	fromBlob(IntBuffer data, Array<long> shape)	Creates a new Tensor instance with dtype torch.int32 with specified shape and data.
static Tensor	fromBlob(FloatBuffer data, Array<long> shape)	Creates a new Tensor instance with dtype torch.float32 with specified shape and data.
static Tensor	fromBlob(LongBuffer data, Array<long> shape)	Creates a new Tensor instance with dtype torch.int64 with specified shape and data.
static Tensor	fromBlob(DoubleBuffer data, Array<long> shape)	Creates a new Tensor instance with dtype torch.float64 with specified shape and data.
long	numel()	Returns the number of elements in this tensor.
static long	numel(Array<long> shape)	Calculates the number of elements in a tensor with the specified shape.
Array<long>	shape()	Returns the shape of this tensor.
abstract DType	dtype()	
Array<byte>	getDataAsByteArray()	
Array<byte>	getDataAsUnsignedByteArray()	
Array<int>	getDataAsIntArray()	
Array<float>	getDataAsFloatArray()	
Array<long>	getDataAsLongArray()	
Array<double>	getDataAsDoubleArray()	
Array<byte>	toByteArray()	Serializes a {@code Tensor} into a byte array.
static Tensor	fromByteArray(Array<byte> bytes)	Deserializes a {@code Tensor} from a byte[].
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait
Method Detail

allocateByteBuffer
static ByteBuffer allocateByteBuffer(int numElements)
Allocates a new direct ByteBuffer with native byte order with specified capacity thatcan be used in fromBlob, .

Parameters:
numElements - capacity (number of elements) of result buffer.
allocateIntBuffer
static IntBuffer allocateIntBuffer(int numElements)
Allocates a new direct IntBuffer with native byte order with specified capacity thatcan be used in fromBlob.

Parameters:
numElements - capacity (number of elements) of result buffer.
allocateFloatBuffer
static FloatBuffer allocateFloatBuffer(int numElements)
Allocates a new direct FloatBuffer with native byte order with specified capacity thatcan be used in fromBlob.

Parameters:
numElements - capacity (number of elements) of result buffer.
allocateLongBuffer
static LongBuffer allocateLongBuffer(int numElements)
Allocates a new direct LongBuffer with native byte order with specified capacity thatcan be used in fromBlob.

Parameters:
numElements - capacity (number of elements) of result buffer.
allocateDoubleBuffer
static DoubleBuffer allocateDoubleBuffer(int numElements)
Allocates a new direct DoubleBuffer with native byte order with specified capacity thatcan be used in fromBlob.

Parameters:
numElements - capacity (number of elements) of result buffer.
fromBlobUnsigned
static Tensor fromBlobUnsigned(Array<byte> data, Array<long> shape)
Creates a new Tensor instance with dtype torch.uint8 with specified shape and data as array ofbytes.

Parameters:
data - Tensor elements
shape - Tensor shape
fromBlob
static Tensor fromBlob(Array<byte> data, Array<long> shape)
Creates a new Tensor instance with dtype torch.int8 with specified shape and data as array ofbytes.

Parameters:
data - Tensor elements
shape - Tensor shape
fromBlob
static Tensor fromBlob(Array<int> data, Array<long> shape)
Creates a new Tensor instance with dtype torch.int32 with specified shape and data as array ofints.

Parameters:
data - Tensor elements
shape - Tensor shape
fromBlob
static Tensor fromBlob(Array<float> data, Array<long> shape)
Creates a new Tensor instance with dtype torch.float32 with specified shape and data as arrayof floats.

Parameters:
data - Tensor elements
shape - Tensor shape
fromBlob
static Tensor fromBlob(Array<long> data, Array<long> shape)
Creates a new Tensor instance with dtype torch.int64 with specified shape and data as array oflongs.

Parameters:
data - Tensor elements
shape - Tensor shape
fromBlob
static Tensor fromBlob(Array<double> data, Array<long> shape)
Creates a new Tensor instance with dtype torch.float64 with specified shape and data as arrayof doubles.

Parameters:
data - Tensor elements
shape - Tensor shape
fromBlobUnsigned
static Tensor fromBlobUnsigned(ByteBuffer data, Array<long> shape)
Creates a new Tensor instance with dtype torch.uint8 with specified shape and data.

Parameters:
data - Direct buffer with native byte order that contains {@code Tensor.numel(shape)} elements.
shape - Tensor shape
fromBlob
static Tensor fromBlob(ByteBuffer data, Array<long> shape)
Creates a new Tensor instance with dtype torch.int8 with specified shape and data.

Parameters:
data - Direct buffer with native byte order that contains {@code Tensor.numel(shape)} elements.
shape - Tensor shape
fromBlob
static Tensor fromBlob(IntBuffer data, Array<long> shape)
Creates a new Tensor instance with dtype torch.int32 with specified shape and data.

Parameters:
data - Direct buffer with native byte order that contains {@code Tensor.numel(shape)} elements.
shape - Tensor shape
fromBlob
static Tensor fromBlob(FloatBuffer data, Array<long> shape)
Creates a new Tensor instance with dtype torch.float32 with specified shape and data.

Parameters:
data - Direct buffer with native byte order that contains {@code Tensor.numel(shape)} elements.
shape - Tensor shape
fromBlob
static Tensor fromBlob(LongBuffer data, Array<long> shape)
Creates a new Tensor instance with dtype torch.int64 with specified shape and data.

Parameters:
data - Direct buffer with native byte order that contains {@code Tensor.numel(shape)} elements.
shape - Tensor shape
fromBlob
static Tensor fromBlob(DoubleBuffer data, Array<long> shape)
Creates a new Tensor instance with dtype torch.float64 with specified shape and data.

Parameters:
data - Direct buffer with native byte order that contains {@code Tensor.numel(shape)} elements.
shape - Tensor shape
numel
long numel()
Returns the number of elements in this tensor.

numel
static long numel(Array<long> shape)
Calculates the number of elements in a tensor with the specified shape.

shape
Array<long> shape()
Returns the shape of this tensor. (The array is a fresh copy.)

dtype
abstract DType dtype()
getDataAsByteArray
Array<byte> getDataAsByteArray()
getDataAsUnsignedByteArray
Array<byte> getDataAsUnsignedByteArray()
getDataAsIntArray
Array<int> getDataAsIntArray()
getDataAsFloatArray
Array<float> getDataAsFloatArray()
getDataAsLongArray
Array<long> getDataAsLongArray()
getDataAsDoubleArray
Array<double> getDataAsDoubleArray()
toByteArray
Array<byte> toByteArray()
Serializes a {@code Tensor} into a byte array. Note: This method is experimental and subject tochange without notice. This does NOT supoprt list type.

fromByteArray
static Tensor fromByteArray(Array<byte> bytes)
Deserializes a {@code Tensor} from a byte[]. Note: This method is experimental and subject tochange without notice. This does NOT supoprt list type.

Parameters:
bytes - The byte array to deserialize from.


Package 
Annotation Experimental

All Implemented Interfaces:
java.lang.annotation.Annotation

public @interface Experimental

                    
This annotation indicates that an API is experimental and may change or be removed at any time. It does not provide any guarantees for API stability or backward-compatibility.

This status is not permanent, and APIs marked with this annotation will need to be either made more robust or removed in the future.

Method Summary

All Methods
Modifier and Type	Method	Description
Methods inherited from class java.lang.annotation.Annotation
annotationType, equals, hashCode, toString
Methods inherited from class java.lang.Object
clone, equals, finalize, getClass, hashCode, notify, notifyAll, toString, wait, wait, wait