int = 42  # Noncompliant {{Rename this variable; it shadows a builtin.}}

a, int = 42  # Noncompliant {{Rename this variable; it shadows a builtin.}}
#  ^^^

def max(a, /):  # Noncompliant {{Rename this function; it shadows a builtin.}}
#   ^^^
    pass

def foo():
    def max(a, /):  # Noncompliant {{Rename this function; it shadows a builtin.}}
    #   ^^^
        pass

foo(x=(max := f(x)))  # Noncompliant {{Rename this variable; it shadows a builtin.}}
#      ^^^

max : int = 42  # Noncompliant {{Rename this variable; it shadows a builtin.}}

class Exception():  # Noncompliant {{Rename this class; it shadows a builtin.}}
#     ^^^^^^^^^
    pass

from my_module import my_max as max  # Noncompliant {{Rename this alias; it shadows a builtin.}}
#                               ^^^

def foo():
    from sys import version_info
    if version_info.major >= 3:
        float = int  # Noncompliant {{Rename this variable; it shadows a builtin.}}
    #   ^^^^^
    try:
        break
    except ValueError:
        float = int  # Noncompliant {{Rename this variable; it shadows a builtin.}}
    #   ^^^^^
    else:
        float = int  # Noncompliant {{Rename this variable; it shadows a builtin.}}
    #   ^^^^^

def craftStarship():
    float: int = 42  # Noncompliant {{Rename this variable; it shadows a builtin.}}
   #^^^^^
    max = max  # Noncompliant {{Rename this variable; it shadows a builtin.}}
   #^^^

class Starship:
    float: int = 42
    max = max

int_ = 42

42: max = int

max: int

foo(x=(max_ := f(x)))

def max_(a, b):
    pass

def process(obj=[]):
    pass

class MyException():
    pass

class MyClass():
    def max(self): pass

from my_module import my_max

from my_module import max  # ok even if it might create some issues
from my_module import max as my_max  # recommended


from sys import version_info
if version_info.major >= 3:
    float = int

try:
    break
except ValueError:
    float = int
else:
    float = int

# Parameter check disabled by default
def process(object=[]):
    pass

lambda max: max

class MyClass:
    def method(self, max=1):
        pass

class SubClass(MyClass):
    def method(self, max=1):
        pass
