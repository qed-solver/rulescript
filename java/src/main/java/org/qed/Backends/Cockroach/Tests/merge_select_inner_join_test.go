package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMergeSelectInnerJoin(t *testing.T) {
	rule := loadRule(t, "MergeSelectInnerJoin")
	e := newEnv(t)
	left, lc := e.scan("u", 2)
	right, rc := e.scan("t", 2)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	above := e.f.ConstructEq(e.v(lc[1]), e.v(rc[1]))
	before := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(left, right, e.filters(on), &memo.JoinPrivate{}),
		e.filters(above),
	)
	want := e.f.ConstructInnerJoin(left, right, e.filters(on, above), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	leftJoin := e.f.ConstructLeftJoin(left, right, e.filters(on), &memo.JoinPrivate{})
	noMatch := e.f.ConstructSelect(leftJoin, e.filters(above))
	e.expectNoMatch(rule, noMatch)
}
