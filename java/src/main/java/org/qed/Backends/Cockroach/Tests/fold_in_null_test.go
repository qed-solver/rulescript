package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldInNull(t *testing.T) {
	rule := loadRule(t, "FoldInNull")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[0]), e.f.ConstructNull(types.Int))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNe(e.v(cols[0]), e.f.ConstructNull(types.Int)))))
}
