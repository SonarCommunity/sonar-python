from typing import Generic, TypeVar

T = TypeVar('T')

class SomeGeneric(Generic[T]):
  ...

class SomeGenericWithTypeParam[T](): ...

class MyImportedChild(SomeGeneric[T]): ...
class MyImportedChild2(SomeGeneric): ...
class MyImportedChild3(SomeGeneric[str]): ...

# U has not been defined
class SomeGenericIncorrectlyDefined(Generic[U]):
    ...
