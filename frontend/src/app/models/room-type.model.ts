export interface RoomType {
  roomTypeId: number;
  name: string;
  description: string | null;
}
export interface RoomTypeRequest {
  name: string;
  description: string | null;
}
