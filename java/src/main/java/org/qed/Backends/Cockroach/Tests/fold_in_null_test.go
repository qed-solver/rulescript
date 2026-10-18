package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldInNull(t *testing.T) {
	rule := loadRule(t, "FoldInNull")
	e := newEnv(t)
	s, c := e.scan("t", 1)
	eq := e.f.ConstructEq(e.v(c[0]), e.f.ConstructNull(types.Int))
	before := e.f.ConstructSelect(s, e.filters(eq))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)

	ne := e.f.ConstructNe(e.v(c[0]), e.f.ConstructNull(types.Int))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(ne)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(c[0]), e.num(1)))))
}
