"""
Comprehensive Python parser test input covering programming constructs for regression testing.

Includes imports, classes, inheritance, methods, module-level functions, and comments.
"""

# Single-line comment

# Another single-line comment for CLOC coverage

from __future__ import annotations

import math
import os.path as osp
from collections import defaultdict
from typing import Any, Callable, List, Optional

BLOCK_NOTE = """
Multi-line
string used like a block comment for documentation density.
"""


class AbstractBase:
    """Package-style abstract base class."""

    def __init__(self, base_value: int) -> None:
        # Field assignment in constructor
        self._base_value = base_value

    def get_base_value(self) -> int:
        """Return stored base value."""
        return self._base_value

    def abstract_hook(self) -> int:
        raise NotImplementedError


class MarkerMixin:
    """Mixin-style interface stand-in."""

    def mark(self) -> None:
        pass

    def default_mark(self) -> None:
        self.mark()


class ParserTestInput(AbstractBase, MarkerMixin):
    """
    Main regression type.

    Covers fields, constructors, methods, parameters, and nested types.
    """

    # Class-level field
    PUBLIC_STATIC_FINAL_INT = 42

    def __init__(self, name: Optional[str] = None, *values: int) -> None:
        """Constructor with optional and varargs-style parameters."""
        super().__init__(len(values) if values else 0)
        self.protected_transient_field = name
        self.nested_generic_field: dict[str, List[int]] = defaultdict(list)

    def run(self) -> None:
        # Single-line comment inside method
        assert self.PUBLIC_STATIC_FINAL_INT > 0

    def call(self) -> str:
        return self.do_work(True, 1, 2, "x", 3, 4, 5.0, 6.0, None)

    def generic_method(self, value: Any) -> Any:
        """Method with a loosely typed parameter."""
        return value

    def do_work(
        self,
        flag: bool,
        b: int,
        s: int,
        c: str,
        i: int,
        l: int,
        f: float,
        d: float,
        obj: Any,
    ) -> bool:
        """Method covering control flow and nested helper."""
        if flag:
            return True
        elif isinstance(obj, str):
            return False

        if i == 0:
            pass
        elif i == 1:
            for n in range(3):
                if n == 1:
                    continue
                if n == 2:
                    break
        else:
            while s > 0:
                s -= 1
            # do-while stand-in
            while True:
                b += 1
                if b >= 3:
                    break

        try:
            if l < 0:
                raise IOError("negative")
        except (IOError, RuntimeError) as ex:
            raise ex
        finally:
            private_flag = abs(i) > 0

        class LocalHelper:
            def help(self) -> None:
                # local class method
                pass

        LocalHelper().help()
        return f > 0.0 and d > 0.0 and c != "\0"

    def abstract_hook(self) -> int:
        return self.get_base_value()

    class StaticNested:
        """Public nested class."""

        def __init__(self, nested_field: int) -> None:
            self.nested_field = nested_field

        def get_nested_field(self) -> int:
            return self.nested_field

    class InnerClass:
        """Inner nested class."""

        def inner_method(self) -> None:
            # accesses enclosing instance via outer methods in real code
            pass

    class NestedEnum:
        """Nested enum-like constants."""

        ALPHA = "ALPHA"
        BETA = "BETA"
        GAMMA = "GAMMA"

        @classmethod
        def is_alpha(cls, value: str) -> bool:
            return value == cls.ALPHA


async def async_helper() -> int:
    """Module-level async function for regression coverage."""
    return 1


def package_private_static_method() -> None:
    # empty module-level function
    pass


def module_helper(path: str, callback: Callable[[str], None]) -> str:
    """Module-level function with parameters."""
    callback(osp.basename(path))
    return math.fabs(-1.0).__class__.__name__


def noop_factory() -> MarkerMixin:
    class Noop(MarkerMixin):
        def mark(self) -> None:
            pass

    return Noop()
