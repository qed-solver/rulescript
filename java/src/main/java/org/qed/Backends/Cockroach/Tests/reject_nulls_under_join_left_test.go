package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRejectNullsUnderJoinLeft(t *testing.T) {
	rule := loadRule(t, "RejectNullsUnderJoinLeft")
	e := newEnv(t)
	left, leftCol := singleColumnScan(e, "t", 0)
	right, rightCol := singleColumnScan(e, "u", 0)
	lc := []opt.ColumnID{leftCol}
	notNull := e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton)
	joinOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rightCol)), notNull)
	before := e.f.ConstructInnerJoin(left, right, joinOn, &memo.JoinPrivate{})
	wantLeft := e.f.ConstructSelect(left, e.filters(e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton)))
	want := e.f.ConstructInnerJoin(wantLeft, right, joinOn, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMissOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rightCol)))
	nearMiss := e.f.ConstructInnerJoin(left, right, nearMissOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
