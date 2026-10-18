package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullBinaryRight(t *testing.T) {
	rule := loadRule(t, "FoldNullBinaryRight")
	e := newEnv(t)
	s, c := e.scan("t", 3)
	nullInt := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(c[0]), nullInt)))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(nullInt, e.v(c[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(c[0]), nullInt))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(c[0]), e.num(1)))))
}
