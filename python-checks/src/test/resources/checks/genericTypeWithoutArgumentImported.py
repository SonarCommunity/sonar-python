from typing import Generic
class SomeGeneric(Generic[T]):
  ...

class SomeGenericWithTypeParam[T](): ...

class MyImportedChild(SomeGeneric[T]): ...
class MyImportedChild2(SomeGeneric): ...
class MyImportedChild3(SomeGeneric[str]): ...
