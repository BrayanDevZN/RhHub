import { useRef } from "react"
import { Canvas, useFrame } from "@react-three/fiber"
import { Float, MeshDistortMaterial, Stars } from "@react-three/drei"
import type { Group } from "three"

function Core() {
  const group = useRef<Group>(null)

  useFrame((_, delta) => {
    if (group.current) group.current.rotation.y += delta * 0.25
  })

  return (
    <group ref={group}>
      <Float speed={1.6} rotationIntensity={0.6} floatIntensity={1.2}>
        <mesh>
          <icosahedronGeometry args={[1.5, 6]} />
          <MeshDistortMaterial color="#818cf8" distort={0.4} speed={1.8} roughness={0.3} metalness={0.25} />
        </mesh>
      </Float>

      <Float speed={2.2} rotationIntensity={1} floatIntensity={2}>
        <mesh position={[2.9, 1.2, -0.5]}>
          <torusKnotGeometry args={[0.35, 0.12, 128, 24]} />
          <meshStandardMaterial color="#22d3ee" roughness={0.2} metalness={0.8} />
        </mesh>
      </Float>

      <Float speed={1.8} rotationIntensity={1.2} floatIntensity={1.6}>
        <mesh position={[-2.8, -1.1, 0.4]}>
          <octahedronGeometry args={[0.55]} />
          <meshStandardMaterial color="#f472b6" roughness={0.25} metalness={0.6} />
        </mesh>
      </Float>

      <Float speed={2.6} rotationIntensity={0.8} floatIntensity={2.2}>
        <mesh position={[-2.2, 1.9, -1]} rotation={[0.6, 0.4, 0]}>
          <torusGeometry args={[0.4, 0.1, 24, 64]} />
          <meshStandardMaterial color="#a78bfa" roughness={0.2} metalness={0.8} />
        </mesh>
      </Float>
    </group>
  )
}

export default function Scene3D({ className }: { className?: string }) {
  return (
    <div className={className} aria-hidden="true">
      <Canvas camera={{ position: [0, 0, 7], fov: 50 }} dpr={[1, 2]}>
        <ambientLight intensity={1.2} />
        <directionalLight position={[4, 5, 5]} intensity={2.2} />
        <pointLight position={[-5, -3, 2]} intensity={25} color="#22d3ee" />
        <pointLight position={[5, -2, -3]} intensity={20} color="#f472b6" />
        <Stars radius={60} depth={40} count={1800} factor={3} fade speed={0.8} />
        <Core />
      </Canvas>
    </div>
  )
}
