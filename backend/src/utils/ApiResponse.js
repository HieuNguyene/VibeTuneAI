/**
 * Standard API Response Structure
 * Đảm bảo tính nhất quán trên toàn bộ REST endpoints cho Android Client Retrofit
 */
class ApiResponse {
  constructor(statusCode, data, message = 'Success') {
    this.success = statusCode < 400;
    this.statusCode = statusCode;
    this.message = message;
    this.data = data;
    this.timestamp = new Date().toISOString();
  }

  static success(res, data, message = 'Success', statusCode = 200) {
    return res.status(statusCode).json(new ApiResponse(statusCode, data, message));
  }

  static created(res, data, message = 'Created successfully') {
    return res.status(201).json(new ApiResponse(201, data, message));
  }

  /**
   * Phản hồi tài nguyên không tồn tại (HTTP 404).
   */
  static notFound(res, message = 'Resource not found') {
    return res.status(404).json(new ApiResponse(404, null, message));
  }
}

module.exports = ApiResponse;
