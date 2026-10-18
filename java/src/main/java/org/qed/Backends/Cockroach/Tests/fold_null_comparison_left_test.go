package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullComparisonLeft(t *testing.T) {
	rule := loadRule(t, "FoldNullComparisonLeft")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	before := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructEq(e.f.ConstructNull(types.Int), e.v(sc[0])),
	))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(
		e.f.ConstructEq(e.num(1), e.v(sc[0])),
	)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(
		e.f.ConstructLt(e.f.ConstructNull(types.Int), e.v(sc[0])),
	)))
}
