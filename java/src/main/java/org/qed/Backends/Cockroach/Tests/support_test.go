package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func (e *env) intValues(cols []opt.ColumnID, rows ...[]int) memo.RelExpr {
	values := make(memo.ScalarListExpr, len(rows))
	rowTypes := make([]*types.T, len(cols))
	for i := range rowTypes {
		rowTypes[i] = types.Int
	}
	for i, row := range rows {
		elems := make(memo.ScalarListExpr, len(row))
		for j, n := range row {
			elems[j] = e.num(n)
		}
		values[i] = e.f.ConstructTuple(elems, types.MakeTuple(rowTypes))
	}
	return e.f.ConstructValues(values, &memo.ValuesPrivate{
		Cols: append(opt.ColList{}, cols...), ID: e.f.Metadata().NextUniqueID(),
	})
}

func (e *env) exists(input memo.RelExpr) opt.ScalarExpr {
	col := e.f.Metadata().AddColumn("exists", types.Bool)
	return e.f.ConstructExists(input, &memo.ExistsPrivate{LazyEvalProjectionCol: col})
}

func twoNonNullColumns(e *env) (memo.RelExpr, []opt.ColumnID) {
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	return e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), []opt.ColumnID{lc[0], rc[0]}
}

func oneColumnUnionAll(e *env) (memo.RelExpr, opt.ColumnID) {
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	return e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
	}), out
}

func expectSQLFires(t *testing.T, rule, sql string) {
	t.Helper()
	plan, fired := planFor(t, sql, nil, rule)
	if len(fired) == 0 {
		t.Fatalf("%s did not fire on %s\n%s", rule, sql, plan)
	}
}

func expectSQLFiresOrNot(t *testing.T, rule, sql string, fires bool) {
	t.Helper()
	plan, fired := planFor(t, sql, nil, rule)
	if (len(fired) > 0) != fires {
		t.Fatalf("%s: unexpected firing on %s: %v\n%s", rule, sql, fired, plan)
	}
}

func expectSQLRewrites(t *testing.T, rule, sql string) {
	t.Helper()
	before, _ := planFor(t, sql, nil)
	got, fired := planFor(t, sql, nil, rule)
	if strings.Join(fired, ",") != rule || got == before {
		t.Fatalf("expected %s to change the SQL plan, got %v:\n%s", rule, fired, got)
	}
}

func expectSQLScanConstraint(t *testing.T, rule, sql, beforeToken, afterMissing string) {
	t.Helper()
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, beforeToken) {
		t.Fatalf("expected %s before rewrite:\n%s", beforeToken, before)
	}
	got, fired := planFor(t, sql, nil, rule)
	if strings.Join(fired, ",") != rule ||
		(afterMissing != "" && strings.Contains(got, afterMissing)) {
		t.Fatalf("expected %s to rewrite plan, got %v:\n%s", rule, fired, got)
	}
}

func expectSQLRepeatedScan(t *testing.T, rule, sql, beforeOp, afterOp string, fires bool) {
	t.Helper()
	helper := func(r opt.RuleName) bool {
		return r == opt.EliminateProject || strings.HasPrefix(r.String(), "Prune")
	}
	before, bFired := planFor(t, sql, helper)
	after, fired := planFor(t, sql, helper, rule)
	if len(bFired) != 0 || !strings.Contains(before, beforeOp) || !strings.Contains(after, afterOp) {
		t.Fatalf("unexpected planner shape for %s: before %v:\n%s\nafter %v:\n%s", rule, bFired, before, fired, after)
	}
	if fires {
		if strings.Join(fired, ",") != rule || strings.Contains(after, beforeOp) {
			t.Fatalf("%s did not rewrite SQL plan: %v:\n%s", rule, fired, after)
		}
	} else if len(fired) != 0 || before != after {
		t.Fatalf("%s unexpectedly matched a different SQL shape: %v:\n%s", rule, fired, after)
	}
}

type sqlHelpers struct {
	limits map[string]int
	names  []string
}

func (h sqlHelpers) predicate() func(opt.RuleName) bool {
	counts := make(map[string]int)
	return func(r opt.RuleName) bool {
		name := r.String()
		if limit, ok := h.limits[name]; ok {
			counts[name]++
			return counts[name] <= limit
		}
		for _, want := range h.names {
			if want == name || (strings.HasSuffix(want, "*") && strings.HasPrefix(name, strings.TrimSuffix(want, "*"))) {
				return true
			}
		}
		return false
	}
}

func expectSQLFiresWith(t *testing.T, rule, sql string, h sqlHelpers) {
	t.Helper()
	before, _ := planFor(t, sql, h.predicate())
	got, fired := planFor(t, sql, h.predicate(), rule)
	if strings.Join(fired, ",") != rule || got == before {
		t.Fatalf("expected %s to change the SQL plan, got %v:\n%s", rule, fired, got)
	}
}
