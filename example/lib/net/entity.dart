class BaseEntity<T> {
  int code;
  String message;
  String status;
  T data;

  BaseEntity(this.code, this.status, this.message, this.data);
}
