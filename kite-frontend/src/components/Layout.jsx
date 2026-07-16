import { Container } from 'react-bootstrap'
import { Outlet } from 'react-router-dom'
import Navbar from './Navbar'

export default function Layout() {
  return (
    <>
      <Navbar />
      <Container className="py-4" style={{ maxWidth: 480 }}>
        <Outlet />
      </Container>
    </>
  )
}
