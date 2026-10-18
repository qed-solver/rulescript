package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullBinaryLeft(t *testing.T) {
	rule := loadRule(t, "FoldNullBinaryLeft")
	e := newEnv(t)
	src, sc := e.scan("t", 1)
	nullInt := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructLt(nullInt, e.v(sc[0]))))
	after := e.f.ConstructSelect(src, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructLt(e.v(sc[0]), nullInt))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructEq(nullInt, e.v(sc[0])))))
}
