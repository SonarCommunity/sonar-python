class HttpResponseBase(Iterable[Any]):
    def set_cookie(
            self,
            key: str,
            value: str = ...,
            max_age: Optional[int] = ...,
            expires: Optional[Any] = ...,
            path: str = ...,
            domain: Optional[str] = ...,
            secure: bool = ...,
            httponly: bool = ...,
            samesite: str = ...,
    ) -> None: ...
    def set_signed_cookie(self, key: str, value: str, salt: str = ..., **kwargs: Any) -> None: ...
    def __setitem__(self, header: Any, value: Any) -> None: ...
    def setdefault(self, key: Any, value: Any) -> None: ...

class HttpResponse(HttpResponseBase): ...
class HttpResponseRedirect(HttpResponseBase): ...
class HttpResponsePermanentRedirect(HttpResponseBase): ...
class HttpResponseNotModified(HttpResponseBase): ...
class HttpResponseNotFound(HttpResponseBase): ...
class HttpResponseForbidden(HttpResponseBase): ...
class HttpResponseNotAllowed(HttpResponseBase): ...
class HttpResponseGone(HttpResponseBase): ...
class HttpResponseServerError(HttpResponseBase): ...
class HttpResponseBadRequest(HttpResponseBase): ...
